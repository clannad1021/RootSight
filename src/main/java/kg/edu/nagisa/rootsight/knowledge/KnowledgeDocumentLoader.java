package kg.edu.nagisa.rootsight.knowledge;

import kg.edu.nagisa.rootsight.common.constant.ExceptionMessages;
import kg.edu.nagisa.rootsight.config.KnowledgeProperties;
import kg.edu.nagisa.rootsight.knowledge.model.KnowledgeDocumentBatch;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 从运维配置的目录读取有限 Markdown 文件，并转换为带来源元数据的确定性知识分块。
 */
@Component
@RequiredArgsConstructor
public class KnowledgeDocumentLoader {

    private static final List<Character> PUNCTUATION_MARKS =
            List.of('.', '?', '!', '\n', ';', ':', '。', '？', '！', '；');

    private final KnowledgeProperties properties;

    /**
     * 扫描白名单文件、计算整体内容版本并生成适合 Embedding 的知识分块。
     */
    public KnowledgeDocumentBatch load() {
        Path root = properties.sourceRoot().toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalStateException(ExceptionMessages.KNOWLEDGE_SOURCE_UNAVAILABLE);
        }
        try {
            List<Path> sourceFiles = discoverSourceFiles(root);
            if (sourceFiles.isEmpty()) {
                throw new IllegalStateException(ExceptionMessages.KNOWLEDGE_SOURCE_EMPTY);
            }
            Map<String, String> contents = readSourceFiles(root, sourceFiles);
            String version = calculateVersion(contents);
            return new KnowledgeDocumentBatch(
                    version, contents.size(), splitDocuments(contents, version)
            );
        } catch (IOException exception) {
            throw new IllegalStateException(ExceptionMessages.KNOWLEDGE_SOURCE_UNAVAILABLE, exception);
        }
    }

    /**
     * 仅保留匹配 include 且不匹配 exclude 的普通 Markdown 文件，并应用数量上限。
     */
    private List<Path> discoverSourceFiles(Path root) throws IOException {
        List<PathMatcher> includeMatchers = pathMatchers(properties.includeGlobs());
        List<PathMatcher> excludeMatchers = pathMatchers(properties.excludeGlobs());
        int maximum = Math.max(1, properties.maxSourceFiles());
        try (var paths = Files.walk(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> matches(root.relativize(path), includeMatchers))
                    .filter(path -> !matches(root.relativize(path), excludeMatchers))
                    .sorted(Comparator.comparing(path -> portablePath(root.relativize(path))))
                    .limit(maximum)
                    .toList();
        }
    }

    /**
     * 将配置的 glob 转换为当前操作系统的 PathMatcher，空列表不会匹配任何文件。
     */
    private static List<PathMatcher> pathMatchers(List<String> globs) {
        if (globs == null) {
            return List.of();
        }
        return globs.stream()
                .filter(glob -> glob != null && !glob.isBlank())
                .map(glob -> FileSystems.getDefault().getPathMatcher("glob:" + glob))
                .toList();
    }

    /**
     * 判断相对路径是否命中任意配置规则。
     */
    private static boolean matches(Path relativePath, List<PathMatcher> matchers) {
        return matchers.stream().anyMatch(matcher -> matcher.matches(relativePath));
    }

    /**
     * 按 UTF-8 读取有限大小的文件，并只保存相对路径，避免绝对目录进入模型上下文。
     */
    private Map<String, String> readSourceFiles(Path root, List<Path> sourceFiles) throws IOException {
        long maximumBytes = Math.max(1, properties.maxFileSize().toBytes());
        Path realRoot = root.toRealPath();
        Map<String, String> contents = new LinkedHashMap<>();
        for (Path sourceFile : sourceFiles) {
            Path realSource = sourceFile.toRealPath();
            // 符号链接指向知识根目录之外时跳过，避免白名单相对路径被用于读取外部文件。
            if (!realSource.startsWith(realRoot) || Files.size(realSource) > maximumBytes) {
                continue;
            }
            String content = Files.readString(realSource, StandardCharsets.UTF_8).trim();
            if (!content.isEmpty()) {
                contents.put(portablePath(root.relativize(sourceFile)), content);
            }
        }
        if (contents.isEmpty()) {
            throw new IllegalStateException(ExceptionMessages.KNOWLEDGE_SOURCE_EMPTY);
        }
        return contents;
    }

    /**
     * 对路径和正文按稳定顺序计算 SHA-256，作为知识库版本与幂等同步依据。
     */
    private static String calculateVersion(Map<String, String> contents) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            contents.forEach((path, content) -> {
                digest.update(path.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(content.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            });
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(ExceptionMessages.KNOWLEDGE_INDEX_FAILED, exception);
        }
    }

    /**
     * 使用有限 Token 分块，并重建确定性 ID 与可信来源元数据。
     */
    private List<Document> splitDocuments(Map<String, String> contents, String version) {
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(Math.max(100, properties.chunkSize()))
                .withMinChunkSizeChars(Math.max(20, properties.minChunkSizeChars()))
                .withMinChunkLengthToEmbed(Math.max(1, properties.minChunkLengthToEmbed()))
                .withMaxNumChunks(Math.max(1, properties.maxChunksPerFile()))
                .withKeepSeparator(true)
                .withPunctuationMarks(PUNCTUATION_MARKS)
                .build();
        List<Document> chunks = new ArrayList<>();
        contents.forEach((source, content) -> {
            Document sourceDocument = Document.builder()
                    .text(content)
                    .metadata(baseMetadata(source, version))
                    .build();
            List<Document> split = splitter.apply(List.of(sourceDocument));
            for (int index = 0; index < split.size(); index++) {
                String text = split.get(index).getText();
                Map<String, Object> metadata = new LinkedHashMap<>(baseMetadata(source, version));
                metadata.put("chunk_index", index);
                chunks.add(Document.builder()
                        .id(deterministicId(source, version, index))
                        .text(text)
                        .metadata(metadata)
                        .build());
            }
        });
        return List.copyOf(chunks);
    }

    /**
     * 创建检索过滤和证据溯源所需的最小元数据，不保存宿主机绝对路径。
     */
    private Map<String, Object> baseMetadata(String source, String version) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("system_name", properties.systemName());
        metadata.put("source", source);
        metadata.put("index_version", version);
        metadata.put("knowledge_kind", "OPERATIONAL_DOCUMENTATION");
        return metadata;
    }

    /**
     * 由系统、来源、版本和分块序号生成稳定 UUID，使同版本重复写入保持幂等。
     */
    private String deterministicId(String source, String version, int chunkIndex) {
        String key = properties.systemName() + "\n" + source + "\n" + version + "\n" + chunkIndex;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    /**
     * 将相对路径统一为跨平台的正斜杠形式，便于模型引用和测试比较。
     */
    private static String portablePath(Path path) {
        return path.toString().replace('\\', '/');
    }
}

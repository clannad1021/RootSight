package kg.edu.nagisa.rootsight.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;
import java.util.List;

/**
 * 运行知识源、分块规则和相似度检索的安全边界配置。
 */
@ConfigurationProperties(prefix = "rootsight.knowledge")
public record KnowledgeProperties(
        boolean enabled,
        boolean autoIndex,
        Path sourceRoot,
        String systemName,
        List<String> includeGlobs,
        List<String> excludeGlobs,
        int maxSourceFiles,
        DataSize maxFileSize,
        int chunkSize,
        int minChunkSizeChars,
        int minChunkLengthToEmbed,
        int maxChunksPerFile,
        int topK,
        int maxTopK,
        double similarityThreshold,
        int maxQueryLength,
        int maxSnippetLength
) {
}

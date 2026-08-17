package kg.edu.nagisa.rootsight.knowledge;

import kg.edu.nagisa.rootsight.common.constant.ExceptionMessages;
import kg.edu.nagisa.rootsight.config.InfrastructureTargetProperties;
import kg.edu.nagisa.rootsight.config.KnowledgeProperties;
import kg.edu.nagisa.rootsight.tool.evidence.KnowledgeSearchEvidence;
import kg.edu.nagisa.rootsight.tool.evidence.KnowledgeSnippetEvidence;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStoreRetriever;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 对 Qdrant 暴露只读相似度检索，并将文档结果压缩为有来源标识的模型证据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeRetrievalService {

    private static final String EVIDENCE_SOURCE = "REAL";
    private static final String EVIDENCE_KIND = "OPERATIONAL_KNOWLEDGE";
    /**
     * AVAILABLE	Qdrant 检索成功，并找到相关文档
     * NO_MATCH	Qdrant 工作正常，但没有文档达到相似度阈值
     * UNAVAILABLE	知识功能关闭，或 Qdrant/Embedding 服务不可用
     * INVALID_REQUEST	用户问题为空、太长或包含非法控制字符
     * CONTROL_CHARACTER_PATTERN 找出查询字符串中的非法控制字符
     */
    private static final String STATUS_AVAILABLE = "AVAILABLE";
    private static final String STATUS_NO_MATCH = "NO_MATCH";
    private static final String STATUS_UNAVAILABLE = "UNAVAILABLE";
    private static final String STATUS_INVALID_REQUEST = "INVALID_REQUEST";
    private static final Pattern CONTROL_CHARACTER_PATTERN = Pattern.compile("[\\p{Cntrl}&&[^\\t]]");

    //把问题交给 VectorStoreRetriever,转向量相似度检索
    private final VectorStoreRetriever vectorStoreRetriever;
    private final KnowledgeProperties properties;
    private final InfrastructureTargetProperties targetProperties;

    /**
     * 在当前知识系统范围内执行语义检索，并明确标记结果不是实时运行证据。
     */
    public KnowledgeSearchEvidence search(String query, Integer requestedTopK) {
        long startedAt = System.nanoTime();
        if (!properties.enabled()) {
            return unavailableEvidence(startedAt);
        }
        String normalizedQuery;
        int topK;
        try {
            normalizedQuery = normalizeQuery(query);
            topK = normalizeTopK(requestedTopK);
        } catch (IllegalArgumentException exception) {
            return invalidEvidence(startedAt);
        }
        try { //创建 Qdrant 元数据过滤条件构造器
            FilterExpressionBuilder filters = new FilterExpressionBuilder();
            List<Document> documents = vectorStoreRetriever.similaritySearch(SearchRequest.builder()
                    .query(normalizedQuery)
                    .topK(topK)
                    .similarityThreshold(normalizeThreshold())//设置达到多少相似度才返回
                    .filterExpression(filters.eq("system_name", properties.systemName()).build())
                    .build());
            List<KnowledgeSnippetEvidence> snippets = documents.stream()
                    .map(this::toSnippet)
                    .toList();
            return new KnowledgeSearchEvidence(
                    EVIDENCE_SOURCE, EVIDENCE_KIND, false,
                    targetProperties.name(), properties.systemName(),
                    snippets.isEmpty() ? STATUS_NO_MATCH : STATUS_AVAILABLE,
                    true, elapsedMillis(startedAt), snippets.size(), snippets,
                    snippets.isEmpty()
                            ? "知识库查询成功，但没有达到相似度阈值的文档片段"
                            : "知识库语义检索成功；结果仅表示系统文档和运行手册知识"
            );
        } catch (RuntimeException exception) {
            // 只记录异常类型，避免查询、文档正文、向量或 Qdrant 连接信息进入应用日志。
            log.warn("Operational knowledge query failed: {}", exception.getClass().getSimpleName());
            return unavailableEvidence(startedAt);
        }
    }

    /**
     * 校验查询非空、长度有限且不包含控制字符，避免异常输入消耗 Embedding 配额。y
     */
    private String normalizeQuery(String query) {
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException(ExceptionMessages.KNOWLEDGE_QUERY_INVALID);
        }
        String normalized = query.trim();
        if (normalized.length() > Math.max(1, properties.maxQueryLength())
                || CONTROL_CHARACTER_PATTERN.matcher(normalized).find()) {
            throw new IllegalArgumentException(ExceptionMessages.KNOWLEDGE_QUERY_INVALID);
        }
        return normalized;
    }

    /**
     * 将返回数量限制在 1 到运维配置上限之间，未提供时采用默认数量。y
     */
    private int normalizeTopK(Integer requestedTopK) {
        int maximum = Math.max(1, properties.maxTopK());
        int fallback = Math.max(1, Math.min(properties.topK(), maximum));
        return requestedTopK == null ? fallback : Math.max(1, Math.min(requestedTopK, maximum));
    }

    /**
     * 将相似度阈值限制在合法的 0 到 1 区间，配置越界时使用安全边界值。y
     */
    private double normalizeThreshold() {
        return Math.max(0, Math.min(1, properties.similarityThreshold()));
    }

    /**
     * 将 Spring AI Document 转换为限长片段，只返回可信相对来源和必要检索元数据。y
     */
    private KnowledgeSnippetEvidence toSnippet(Document document) {
        String source = String.valueOf(document.getMetadata().getOrDefault("source", "unknown"));
        int chunkIndex = intMetadata(document, "chunk_index");
        String content = sanitizeSnippet(document.getText());
        return new KnowledgeSnippetEvidence(source, chunkIndex, document.getScore(), content);
    }

    /**
     * 安全读取整数元数据，类型或格式不符时使用零作为未知分块序号。y
     */
    private static int intMetadata(Document document, String key) {
        Object value = document.getMetadata().get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? 0 : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    /**
     * 合并多余空白并限制片段长度，降低模型上下文成本和超长文档泄露范围。y
     */
    private String sanitizeSnippet(String text) {
        String normalized = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        int maximum = Math.max(100, properties.maxSnippetLength());
        return normalized.length() <= maximum
                ? normalized
                : normalized.substring(0, maximum) + "...[TRUNCATED]";
    }

    /**
     * 构造非法检索条件证据，不回显可疑查询内容。y
     */
    private KnowledgeSearchEvidence invalidEvidence(long startedAt) {
        return new KnowledgeSearchEvidence(
                EVIDENCE_SOURCE, EVIDENCE_KIND, false,
                targetProperties.name(), properties.systemName(), STATUS_INVALID_REQUEST,
                false, elapsedMillis(startedAt), 0, List.of(),
                ExceptionMessages.KNOWLEDGE_QUERY_INVALID
        );
    }

    /**
     * 构造知识功能关闭或依赖不可用证据，不向模型暴露 API 和数据库连接细节。y
     */
    private KnowledgeSearchEvidence unavailableEvidence(long startedAt) {
        return new KnowledgeSearchEvidence(
                EVIDENCE_SOURCE, EVIDENCE_KIND, false,
                targetProperties.name(), properties.systemName(), STATUS_UNAVAILABLE,
                false, elapsedMillis(startedAt), 0, List.of(),
                ExceptionMessages.KNOWLEDGE_QUERY_FAILED
        );
    }

    /**
     * 计算从指定单调时钟起点到当前时刻的毫秒耗时。y
     */
    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}

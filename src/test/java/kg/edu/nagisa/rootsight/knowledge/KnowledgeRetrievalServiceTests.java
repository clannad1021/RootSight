package kg.edu.nagisa.rootsight.knowledge;

import kg.edu.nagisa.rootsight.common.constant.ExceptionMessages;
import kg.edu.nagisa.rootsight.config.InfrastructureTargetProperties;
import kg.edu.nagisa.rootsight.config.KnowledgeProperties;
import kg.edu.nagisa.rootsight.tool.evidence.KnowledgeSearchEvidence;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStoreRetriever;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 验证知识检索的来源标识、查询边界和安全失败行为。
 */
class KnowledgeRetrievalServiceTests {

    /**
     * 验证相似文档会转换为非实时知识证据，并保留相对来源与相似度。
     */
    @Test
    void shouldReturnDocumentationAsNonRealtimeEvidence() {
        VectorStoreRetriever retriever = mock(VectorStoreRetriever.class);
        Document document = Document.builder()
                .id("doc-1")
                .text("Redis 异常时采用 fail-open，并回源 MySQL。")
                .metadata(Map.of("source", "docs/architecture.md", "chunk_index", 3))
                .score(0.91)
                .build();
        given(retriever.similaritySearch(any(SearchRequest.class))).willReturn(List.of(document));
        KnowledgeRetrievalService service = service(retriever);

        KnowledgeSearchEvidence evidence = service.search("Redis 挂了为什么 HTTP 还能访问", 3);

        assertThat(evidence.status()).isEqualTo("AVAILABLE");
        assertThat(evidence.realtimeEvidence()).isFalse();
        assertThat(evidence.evidenceKind()).isEqualTo("OPERATIONAL_KNOWLEDGE");
        assertThat(evidence.snippets()).singleElement().satisfies(snippet -> {
            assertThat(snippet.source()).isEqualTo("docs/architecture.md");
            assertThat(snippet.chunkIndex()).isEqualTo(3);
            assertThat(snippet.similarityScore()).isEqualTo(0.91);
        });
    }

    /**
     * 验证包含控制字符的查询在调用 Embedding 和向量库前被拒绝。
     */
    @Test
    void shouldRejectUnsafeQueryBeforeVectorSearch() {
        VectorStoreRetriever retriever = mock(VectorStoreRetriever.class);
        KnowledgeRetrievalService service = service(retriever);

        KnowledgeSearchEvidence evidence = service.search("Redis\nfilter=other", 3);

        verifyNoInteractions(retriever);
        assertThat(evidence.status()).isEqualTo("INVALID_REQUEST");
        assertThat(evidence.detail()).isEqualTo(ExceptionMessages.KNOWLEDGE_QUERY_INVALID);
        assertThat(evidence.toString()).doesNotContain("filter=other");
    }

    /**
     * 验证向量库故障转换为脱敏 UNAVAILABLE 证据，不抛出底层连接异常。
     */
    @Test
    void shouldReturnUnavailableEvidenceWhenVectorStoreFails() {
        VectorStoreRetriever retriever = mock(VectorStoreRetriever.class);
        given(retriever.similaritySearch(any(SearchRequest.class)))
                .willThrow(new IllegalStateException("secret-qdrant:6334"));
        KnowledgeRetrievalService service = service(retriever);

        KnowledgeSearchEvidence evidence = service.search("查询运行手册", null);

        assertThat(evidence.status()).isEqualTo("UNAVAILABLE");
        assertThat(evidence.detail()).isEqualTo(ExceptionMessages.KNOWLEDGE_QUERY_FAILED);
        assertThat(evidence.toString()).doesNotContain("secret-qdrant");
    }

    /**
     * 创建检索测试服务并注入固定目标与知识边界。
     */
    private static KnowledgeRetrievalService service(VectorStoreRetriever retriever) {
        return new KnowledgeRetrievalService(
                retriever, properties(), new InfrastructureTargetProperties("test-target")
        );
    }

    /**
     * 创建检索测试使用的知识配置。
     */
    private static KnowledgeProperties properties() {
        return new KnowledgeProperties(
                true, false, Path.of("knowledge-base"), "test-system",
                List.of("README.md"), List.of(), 10, DataSize.ofMegabytes(1),
                800, 200, 20, 100, 5, 10, 0.45, 500, 1000
        );
    }
}

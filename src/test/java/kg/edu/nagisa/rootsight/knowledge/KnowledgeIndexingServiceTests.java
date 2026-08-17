package kg.edu.nagisa.rootsight.knowledge;

import kg.edu.nagisa.rootsight.config.KnowledgeProperties;
import kg.edu.nagisa.rootsight.knowledge.model.KnowledgeDocumentBatch;
import kg.edu.nagisa.rootsight.knowledge.model.KnowledgeIndexResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeIndexingServiceTests {

    private KnowledgeDocumentLoader documentLoader;
    private VectorStore vectorStore;
    private QdrantCollectionManager collectionManager;
    private KnowledgeIndexingService indexingService;

    /**
     * 为每个测试创建独立依赖，防止 Mockito 调用记录相互影响。
     */
    @BeforeEach
    void setUp() {
        documentLoader = mock(KnowledgeDocumentLoader.class);
        vectorStore = mock(VectorStore.class);
        collectionManager = mock(QdrantCollectionManager.class);
        indexingService = new KnowledgeIndexingService(
                documentLoader, vectorStore, properties(true), collectionManager
        );
    }

    /**
     * 验证已存在同版本知识时不会重复写入或清理 Qdrant。
     */
    @Test
    void shouldSkipWriteWhenCurrentVersionAlreadyExists() {
        KnowledgeDocumentBatch batch = batch();
        when(documentLoader.load()).thenReturn(batch);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(Document.builder().text("existing").build()));

        KnowledgeIndexResult result = indexingService.synchronizeKnowledge();

        assertThat(result.status()).isEqualTo("UP_TO_DATE");
        verify(collectionManager).ensureCollectionExists();
        verify(vectorStore, never()).add(any());
        verify(vectorStore, never()).delete(any(Filter.Expression.class));
    }

    /**
     * 验证新版本先完整写入，再触发历史版本清理。
     */
    @Test
    void shouldWriteNewVersionAndDeleteStaleVersions() {
        KnowledgeDocumentBatch batch = batch();
        when(documentLoader.load()).thenReturn(batch);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        KnowledgeIndexResult result = indexingService.synchronizeKnowledge();

        assertThat(result.status()).isEqualTo("INDEXED");
        assertThat(result.chunkCount()).isEqualTo(1);
        verify(collectionManager).ensureCollectionExists();
        verify(vectorStore).add(batch.documents());
        verify(vectorStore).delete(any(Filter.Expression.class));
    }

    /**
     * 验证关闭知识能力时连本地文件都不会读取。
     */
    @Test
    void shouldDoNothingWhenKnowledgeIsDisabled() {
        KnowledgeIndexingService disabledService =
                new KnowledgeIndexingService(documentLoader, vectorStore, properties(false), collectionManager);

        KnowledgeIndexResult result = disabledService.synchronizeKnowledge();

        assertThat(result.status()).isEqualTo("DISABLED");
        verify(documentLoader, never()).load();
        verify(collectionManager, never()).ensureCollectionExists();
        verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
    }

    /**
     * 构造一个最小的版本化知识批次，供同步分支测试复用。
     */
    private KnowledgeDocumentBatch batch() {
        Document document = Document.builder()
                .id("f30d7795-b5f0-45b8-8a58-cc1ae81901bf")
                .text("Redis failure should not remove the HTTP fallback path.")
                .metadata("system_name", "short-pan")
                .metadata("index_version", "version-1")
                .build();
        return new KnowledgeDocumentBatch("version-1", 1, List.of(document));
    }

    /**
     * 创建仅调整 enabled 状态的知识配置，保持其余安全边界与生产默认值一致。
     */
    private KnowledgeProperties properties(boolean enabled) {
        return new KnowledgeProperties(
                enabled,
                true,
                Path.of("knowledge-base"),
                "short-pan",
                List.of("README.md", "docs/*.md", "runbooks/*.md"),
                List.of("docs/interview*.md"),
                100,
                DataSize.ofMegabytes(1),
                800,
                200,
                20,
                200,
                5,
                10,
                0.45,
                500,
                1200
        );
    }
}

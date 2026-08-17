package kg.edu.nagisa.rootsight.knowledge;

import kg.edu.nagisa.rootsight.config.KnowledgeProperties;
import kg.edu.nagisa.rootsight.knowledge.model.KnowledgeDocumentBatch;
import kg.edu.nagisa.rootsight.knowledge.model.KnowledgeIndexResult;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

/**
 * 将文件知识以版本化方式同步到 Qdrant，避免每次启动重复调用全部 Embedding。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeIndexingService {

    private final KnowledgeDocumentLoader documentLoader;
    private final VectorStore vectorStore;
    private final KnowledgeProperties properties;
    private final QdrantCollectionManager collectionManager;

    /**
     * 同步当前知识版本：已存在则跳过，否则先写入新版本，成功后再删除旧版本。
     */
    public KnowledgeIndexResult synchronizeKnowledge() {
        if (!properties.enabled()) {
            return new KnowledgeIndexResult("DISABLED", null, 0, 0);
        }
        KnowledgeDocumentBatch batch = documentLoader.load();
        collectionManager.ensureCollectionExists();
        if (currentVersionExists(batch.version())) {
            return new KnowledgeIndexResult(
                    "UP_TO_DATE", batch.version(), batch.sourceCount(), batch.documents().size()
            );
        }
        vectorStore.add(batch.documents());
        deleteStaleVersions(batch.version());
        return new KnowledgeIndexResult(
                "INDEXED", batch.version(), batch.sourceCount(), batch.documents().size()
        );
    }

    /**
     * 使用系统名与版本元数据过滤检查当前版本，查询正文只用于生成一次探测向量。
     */
    private boolean currentVersionExists(String version) {
        FilterExpressionBuilder filters = new FilterExpressionBuilder();
        return !vectorStore.similaritySearch(SearchRequest.builder()
                .query("knowledge index version check")
                .topK(1)
                .similarityThresholdAll()
                .filterExpression(filters.and(
                        filters.eq("system_name", properties.systemName()),
                        filters.eq("index_version", version)
                ).build())
                .build()).isEmpty();
    }

    /**
     * 新版本全部写入成功后删除同一系统的历史分块，避免检索到已经失效的文档内容。
     */
    private void deleteStaleVersions(String currentVersion) {
        FilterExpressionBuilder filters = new FilterExpressionBuilder();
        vectorStore.delete(filters.and(
                filters.eq("system_name", properties.systemName()),
                filters.ne("index_version", currentVersion)
        ).build());
    }
}

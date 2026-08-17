package kg.edu.nagisa.rootsight.knowledge;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import kg.edu.nagisa.rootsight.common.constant.ExceptionMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantVectorStoreProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

/**
 * 在可降级的知识同步阶段检查并创建 Qdrant 集合，避免外部依赖故障阻止应用上下文启动。
 */
@Component
@RequiredArgsConstructor
public class QdrantCollectionManager {

    private final QdrantClient qdrantClient;
    private final EmbeddingModel embeddingModel;
    private final QdrantVectorStoreProperties qdrantProperties;

    /**
     * 集合不存在时按当前 Embedding 维度创建余弦距离集合，存在时保持原数据不变。
     */
    public void ensureCollectionExists() {
        try {
            String collectionName = qdrantProperties.getCollectionName();
            if (qdrantClient.listCollectionsAsync().get().contains(collectionName)) {
                return;
            }
            VectorParams vectorParams = VectorParams.newBuilder()
                    .setDistance(Distance.Cosine)
                    .setSize(embeddingModel.dimensions())
                    .build();
            qdrantClient.createCollectionAsync(collectionName, vectorParams).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ExceptionMessages.KNOWLEDGE_INDEX_FAILED, exception);
        } catch (ExecutionException | RuntimeException exception) {
            throw new IllegalStateException(ExceptionMessages.KNOWLEDGE_INDEX_FAILED, exception);
        }
    }
}

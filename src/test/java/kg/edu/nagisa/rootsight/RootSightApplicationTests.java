package kg.edu.nagisa.rootsight;

import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.ai.deepseek.api-key=test-key",
        "spring.ai.openai.api-key=test-key",
        "rootsight.knowledge.auto-index=false",
        "spring.ai.vectorstore.qdrant.initialize-schema=false"
})
class RootSightApplicationTests {

    @MockitoBean
    private VectorStore vectorStore;

    @Autowired
    private Environment environment;

    /**
     * 验证完整 Spring 容器可以加载知识库与诊断工作流组件。
     */
    @Test
    void contextLoads() {
    }

    /**
     * 验证 Embedding 固定使用普通 BAAI/bge-m3，防止配置回退到 Pro 模型。
     */
    @Test
    void shouldUseNonProBgeM3EmbeddingModel() {
        assertThat(environment.getProperty("spring.ai.openai.embedding.model"))
                .isEqualTo("BAAI/bge-m3")
                .doesNotStartWith("Pro/");
    }

    /**
     * 验证 Stage 6 默认启用有限总时限和 Tool 调用预算。
     */
    @Test
    void shouldLoadControlledWorkflowDefaults() {
        assertThat(environment.getProperty("rootsight.diagnosis.workflow.timeout"))
                .isEqualTo("90s");
        assertThat(environment.getProperty(
                "rootsight.diagnosis.workflow.max-tool-calls", Integer.class
        )).isEqualTo(8);
    }

}

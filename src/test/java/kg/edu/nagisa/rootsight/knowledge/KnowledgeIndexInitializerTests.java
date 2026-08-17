package kg.edu.nagisa.rootsight.knowledge;

import kg.edu.nagisa.rootsight.config.KnowledgeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeIndexInitializerTests {

    /**
     * 验证 Qdrant 或 Embedding 同步失败只降低知识能力，不中断应用启动流程。
     */
    @Test
    void shouldKeepApplicationStartupAvailableWhenSynchronizationFails() {
        KnowledgeIndexingService indexingService = mock(KnowledgeIndexingService.class);
        when(indexingService.synchronizeKnowledge()).thenThrow(new IllegalStateException("unavailable"));
        KnowledgeIndexInitializer initializer =
                new KnowledgeIndexInitializer(indexingService, properties(true, true));

        assertThatCode(() -> initializer.run(mock(ApplicationArguments.class)))
                .doesNotThrowAnyException();
        verify(indexingService).synchronizeKnowledge();
    }

    /**
     * 验证关闭自动索引时启动阶段不会访问任何外部向量服务。
     */
    @Test
    void shouldSkipSynchronizationWhenAutoIndexIsDisabled() {
        KnowledgeIndexingService indexingService = mock(KnowledgeIndexingService.class);
        KnowledgeIndexInitializer initializer =
                new KnowledgeIndexInitializer(indexingService, properties(true, false));

        initializer.run(mock(ApplicationArguments.class));

        verify(indexingService, never()).synchronizeKnowledge();
    }

    /**
     * 创建只调整启用状态的知识配置，供启动降级分支测试复用。
     */
    private KnowledgeProperties properties(boolean enabled, boolean autoIndex) {
        return new KnowledgeProperties(
                enabled,
                autoIndex,
                Path.of("knowledge-base"),
                "observed-system",
                List.of("README.md"),
                List.of(),
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

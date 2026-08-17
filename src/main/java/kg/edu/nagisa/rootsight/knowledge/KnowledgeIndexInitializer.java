package kg.edu.nagisa.rootsight.knowledge;

import kg.edu.nagisa.rootsight.config.KnowledgeProperties;
import kg.edu.nagisa.rootsight.knowledge.model.KnowledgeIndexResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 应用启动后的知识同步入口；知识服务故障不会阻止实时诊断能力启动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeIndexInitializer implements ApplicationRunner {

    private final KnowledgeIndexingService indexingService;
    private final KnowledgeProperties properties;

    /**
     * 在启用自动索引时执行版本化同步，并只记录状态和数量，不记录知识正文或绝对路径。
     */
    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled() || !properties.autoIndex()) {
            return;
        }
        try {
            KnowledgeIndexResult result = indexingService.synchronizeKnowledge();
            log.info("Knowledge synchronization completed: status={}, sources={}, chunks={}",
                    result.status(), result.sourceCount(), result.chunkCount());
        } catch (RuntimeException exception) {
            log.warn("Knowledge synchronization unavailable: {}", exception.getClass().getSimpleName());
        }
    }
}

package kg.edu.nagisa.rootsight.knowledge;

import kg.edu.nagisa.rootsight.config.KnowledgeProperties;
import kg.edu.nagisa.rootsight.knowledge.model.KnowledgeDocumentBatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证知识文件白名单、排除规则、分块来源和稳定版本。
 */
class KnowledgeDocumentLoaderTests {

    @TempDir
    private Path tempDirectory;

    /**
     * 验证只索引允许的 Markdown，并且同一内容生成相同版本和文档 ID。
     */
    @Test
    void shouldLoadOnlyAllowedMarkdownWithDeterministicVersion() throws IOException {
        Files.createDirectories(tempDirectory.resolve("docs"));
        Files.writeString(tempDirectory.resolve("README.md"),
                "# Demo\n\nRedis 故障时请求回源数据库，服务保持可用。".repeat(20));
        Files.writeString(tempDirectory.resolve("docs/architecture.md"),
                "# Architecture\n\nRabbitMQ 用于异步削峰，发布失败不会阻塞主请求。".repeat(20));
        Files.writeString(tempDirectory.resolve("docs/interview-question-bank.md"),
                "这份面试题不应进入运行知识库。".repeat(20));
        KnowledgeDocumentLoader loader = new KnowledgeDocumentLoader(properties(tempDirectory));

        KnowledgeDocumentBatch first = loader.load();
        KnowledgeDocumentBatch second = loader.load();

        assertThat(first.sourceCount()).isEqualTo(2);
        assertThat(first.documents()).isNotEmpty();
        assertThat(first.documents())
                .allSatisfy(document -> assertThat(document.getMetadata().get("source"))
                        .isIn("README.md", "docs/architecture.md"));
        assertThat(first.documents())
                .noneSatisfy(document -> assertThat(document.getText()).contains("面试题"));
        assertThat(second.version()).isEqualTo(first.version());
        assertThat(second.documents()).extracting(document -> document.getId())
                .containsExactlyElementsOf(first.documents().stream().map(document -> document.getId()).toList());
    }

    /**
     * 创建文件加载测试使用的有限知识配置。
     */
    private static KnowledgeProperties properties(Path root) {
        return new KnowledgeProperties(
                true, false, root, "test-system",
                List.of("README.md", "docs/*.md"), List.of("docs/interview*.md"),
                10, DataSize.ofKilobytes(100), 100, 20, 5, 20,
                5, 10, 0.4, 500, 1000
        );
    }
}

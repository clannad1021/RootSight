package kg.edu.nagisa.rootsight.agent.format;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosisAnswerFormatterTests {

    private final DiagnosisAnswerFormatter formatter = new DiagnosisAnswerFormatter();

    @Test
    void shouldRemoveMarkdownNoiseAcrossStreamChunks() {
        DiagnosisAnswerFormatter.StreamFormatter streamFormatter = formatter.createStreamFormatter();

        String firstChunk = streamFormatter.format("## 诊断结论：\n**Redis");
        String secondChunk = streamFormatter.format(" 不可用**\n/ 关键证据：\n- PING 失败");

        assertThat(firstChunk + secondChunk)
                .isEqualTo("诊断结论：\nRedis 不可用\n关键证据：\nPING 失败")
                .doesNotContain("*", "#", "/", "•");
    }

    @Test
    void shouldKeepMeaningfulSlashInsideTechnicalText() {
        DiagnosisAnswerFormatter.StreamFormatter streamFormatter = formatter.createStreamFormatter();

        assertThat(streamFormatter.format("处理建议：\n1. 检查 /api/orders 和 HTTP/2"))
                .isEmpty();

        assertThat(streamFormatter.format("诊断结论：\n目标服务异常。\n处理建议：\n1. 检查 /api/orders 和 HTTP/2"))
                .isEqualTo("诊断结论：\n目标服务异常。\n处理建议：\n1. 检查 /api/orders 和 HTTP/2");
    }

    @Test
    void shouldDiscardProcessPreambleBeforeStructuredReport() {
        DiagnosisAnswerFormatter.StreamFormatter streamFormatter = formatter.createStreamFormatter();

        assertThat(streamFormatter.format("我将先调用必要工具收集证据。\n诊断结"))
                .isEmpty();
        assertThat(streamFormatter.format("论：\n目标服务异常。"))
                .isEqualTo("诊断结论：\n目标服务异常。");
    }
}

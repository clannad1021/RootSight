package kg.edu.nagisa.rootsight.tool.fake;

import kg.edu.nagisa.rootsight.agent.trace.ToolCallTraceRecorder;
import kg.edu.nagisa.rootsight.tool.evidence.HttpMetricsEvidence;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Stage 1B 的指标模拟 Tool，用固定证据验证模型能否主动查询服务现象。
 */
@Component
@RequiredArgsConstructor
public class FakeMetricsTool {

    private final ToolCallTraceRecorder traceRecorder;

    /**
     * 查询目标服务的模拟 HTTP 指标。
     */
    @Tool(name = "query_service_http_metrics",
            description = "查询目标服务的 HTTP QPS、P95 延迟、成功率和整体状态。仅在这些指标有助于回答当前问题时调用。")
    public HttpMetricsEvidence queryServiceHttpMetrics(
            @ToolParam(description = "需要诊断的目标服务名称") String targetService,
            ToolContext toolContext) {
        HttpMetricsEvidence evidence = new HttpMetricsEvidence(
                "DEMO",
                targetService,
                42.0,
                920,
                99.8,
                "DEGRADED"
        );
        traceRecorder.record(toolContext, "query_service_http_metrics",
                "[DEMO] 目标=" + targetService + "，P95=920ms，成功率=99.8%");
        return evidence;
    }
}

package kg.edu.nagisa.rootsight.tool.infrastructure;

import kg.edu.nagisa.rootsight.agent.trace.ToolCallTraceRecorder;
import kg.edu.nagisa.rootsight.agent.workflow.DiagnosisWorkflowCoordinator;
import kg.edu.nagisa.rootsight.infrastructure.prometheus.PrometheusMetricsClient;
import kg.edu.nagisa.rootsight.tool.evidence.PrometheusMetricsEvidence;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 向 Agent 暴露 Prometheus 真实指标的受控只读查询能力。
 */
@Component
@RequiredArgsConstructor
public class PrometheusMetricsInspectionTool {

    private final PrometheusMetricsClient prometheusMetricsClient;
    private final ToolCallTraceRecorder traceRecorder;
    private final DiagnosisWorkflowCoordinator workflowCoordinator;

    /**
     * 查询目标服务的可用性、HTTP 流量与延迟、进程 CPU 和 JVM 资源指标。
     */
    @Tool(name = "query_service_http_metrics",
            description = "查询 Prometheus 中目标服务的真实可用性、HTTP QPS、5xx 错误率、成功率、P95/P99 延迟、进程 CPU 和 JVM 指标。可提供带时区的故障时间与有限窗口；PromQL 由后端固定生成。")
    public PrometheusMetricsEvidence queryServiceHttpMetrics(
            @ToolParam(required = false,
                    description = "目标服务的 application 标签值；不确定时省略并使用当前配置的默认服务")
            String targetService,
            @ToolParam(required = false,
                    description = "可选故障时间，必须是带 Z 或时区偏移的 ISO-8601 时间")
            String incidentTime,
            @ToolParam(required = false,
                    description = "可选统计窗口，仅允许 1m、5m、15m、30m 或 1h")
            String window,
            ToolContext toolContext) {
        workflowCoordinator.beforeToolCall(toolContext);
        PrometheusMetricsEvidence evidence = prometheusMetricsClient.queryMetrics(
                targetService, incidentTime, window
        );
        traceRecorder.record(toolContext, "query_service_http_metrics",
                "[REAL] 目标=" + evidence.targetName()
                        + "，服务=" + evidence.targetService()
                        + "，Prometheus=" + evidence.status()
                        + "，窗口=" + evidence.window()
                        + "，耗时=" + evidence.responseTimeMs() + "ms");
        return evidence;
    }
}

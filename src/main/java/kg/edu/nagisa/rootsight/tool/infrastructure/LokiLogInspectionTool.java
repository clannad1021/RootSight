package kg.edu.nagisa.rootsight.tool.infrastructure;

import kg.edu.nagisa.rootsight.agent.trace.ToolCallTraceRecorder;
import kg.edu.nagisa.rootsight.infrastructure.loki.LokiLogClient;
import kg.edu.nagisa.rootsight.tool.evidence.LokiLogEvidence;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 向 Agent 暴露 Loki 真实日志的受控只读查询能力。
 */
@Component
@RequiredArgsConstructor
public class LokiLogInspectionTool {

    private final LokiLogClient lokiLogClient;
    private final ToolCallTraceRecorder traceRecorder;

    /**
     * 查询目标服务的 ERROR/WARN 日志，支持故障时间锚点、有限文本过滤和后端自动扩窗。
     */
    @Tool(name = "query_application_logs",
            description = "查询 Loki 中目标服务的真实 ERROR/WARN 日志。可提供带时区的故障时间、关键词或 traceId；短窗口无结果时后端会在有界范围内自动扩窗，并明确标记历史兜底证据。模型不得生成 LogQL。")
    public LokiLogEvidence queryApplicationLogs(
            @ToolParam(required = false,
                    description = "目标服务的 service_name 标签值；不确定时省略并使用当前配置的默认服务")
            String targetService,
            @ToolParam(required = false,
                    description = "可选故障时间，必须是带 Z 或时区偏移的 ISO-8601 时间，例如 2026-08-14T10:30:00+08:00")
            String incidentTime,
            @ToolParam(required = false,
                    description = "可选日志关键词，只执行经过转义的精确包含过滤，不接受 LogQL")
            String keyword,
            @ToolParam(required = false,
                    description = "可选 traceId，用于缩小日志范围和补查同一调用链上下文")
            String traceId,
            @ToolParam(required = false,
                    description = "可选返回数量；后端会强制限制在安全上限以内")
            Integer limit,
            ToolContext toolContext) {
        LokiLogEvidence evidence = lokiLogClient.queryLogs(
                targetService, incidentTime, keyword, traceId, limit
        );
        traceRecorder.record(toolContext, "query_application_logs",
                "[REAL] 目标=" + evidence.targetName()
                        + "，服务=" + evidence.targetService()
                        + "，Loki=" + evidence.status()
                        + "，策略=" + evidence.strategy()
                        + "，命中=" + evidence.matchedCount()
                        + "，耗时=" + evidence.responseTimeMs() + "ms");
        return evidence;
    }
}

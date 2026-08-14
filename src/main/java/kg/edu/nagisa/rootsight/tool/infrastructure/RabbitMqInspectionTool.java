package kg.edu.nagisa.rootsight.tool.infrastructure;

import kg.edu.nagisa.rootsight.agent.trace.ToolCallTraceRecorder;
import kg.edu.nagisa.rootsight.infrastructure.rabbitmq.RabbitMqStatusClient;
import kg.edu.nagisa.rootsight.tool.evidence.RabbitMqStatusEvidence;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * 向 Agent 暴露 RabbitMQ Management API 的真实只读状态检查能力。
 */
@Component
@RequiredArgsConstructor
public class RabbitMqInspectionTool {

    private final RabbitMqStatusClient rabbitMqStatusClient;
    private final ToolCallTraceRecorder traceRecorder;

    /**
     * 检查当前配置目标的 RabbitMQ 节点概览与指定 vhost 队列积压、消费者和运行状态。
     */
    @Tool(name = "inspect_rabbitmq_status",
            description = "读取当前目标 RabbitMQ 的真实版本、集群、指定 vhost 队列数量、消息积压、未确认消息、消费者和队列状态。仅在这些证据有助于回答当前问题时调用。")
    public RabbitMqStatusEvidence inspectRabbitMqStatus(ToolContext toolContext) {
        RabbitMqStatusEvidence evidence = rabbitMqStatusClient.inspectStatus();
        traceRecorder.record(toolContext, "inspect_rabbitmq_status",
                "[REAL] 目标=" + evidence.targetName()
                        + "，RabbitMQ=" + evidence.status()
                        + "，vhost=" + evidence.vhost()
                        + "，队列=" + evidence.totalQueueCount()
                        + "，耗时=" + evidence.responseTimeMs() + "ms");
        return evidence;
    }
}

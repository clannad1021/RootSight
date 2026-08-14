package kg.edu.nagisa.rootsight.tool.evidence;

import java.util.List;

/**
 * RabbitMQ Management API 返回的真实只读状态证据。
 *
 * <p>队列数量较多时只检查有界分页并返回有限样本，避免大响应占满模型上下文。</p>
 */
public record RabbitMqStatusEvidence(
        String evidenceSource,
        String targetName,
        String component,
        String status,
        boolean reachable,
        long responseTimeMs,
        String version,
        String clusterName,
        String vhost,
        long totalQueueCount,
        int inspectedQueueCount,
        boolean queueResultTruncated,
        long sampledMessages,
        long sampledMessagesReady,
        long sampledMessagesUnacknowledged,
        long sampledConsumers,
        List<RabbitMqQueueEvidence> queues,
        String detail
) {
}

package kg.edu.nagisa.rootsight.tool.evidence;

/**
 * RabbitMQ 单个队列的只读状态摘要，不包含消息正文和连接凭证。
 */
public record RabbitMqQueueEvidence(
        String name,
        String state,
        long messages,
        long messagesReady,
        long messagesUnacknowledged,
        long consumers
) {
}

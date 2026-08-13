package kg.edu.nagisa.rootsight.tool.evidence;

/**
 * Redis 只读状态证据；数值为空表示连接失败或服务端未返回该指标。
 */
public record RedisStatusEvidence(
        String evidenceSource,
        String targetName,
        String component,
        String status,
        boolean reachable,
        long responseTimeMs,
        String ping,
        boolean metricsAvailable,
        String version,
        String role,
        Long connectedClients,
        Long usedMemoryBytes,
        Long totalCommandsProcessed,
        Long keyspaceHits,
        Long keyspaceMisses,
        String detail
) {
}

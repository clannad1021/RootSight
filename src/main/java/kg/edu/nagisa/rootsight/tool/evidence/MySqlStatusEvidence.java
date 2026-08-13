package kg.edu.nagisa.rootsight.tool.evidence;

/**
 * MySQL 只读状态证据；只包含服务状态和负载概览，不读取业务表数据。
 */
public record MySqlStatusEvidence(
        String evidenceSource,
        String targetName,
        String component,
        String status,
        boolean reachable,
        long responseTimeMs,
        String version,
        String host,
        Integer port,
        Boolean readOnlyMode,
        Long uptimeSeconds,
        Long threadsConnected,
        Long threadsRunning,
        Long totalQueries,
        Long slowQueries,
        String detail
) {
}

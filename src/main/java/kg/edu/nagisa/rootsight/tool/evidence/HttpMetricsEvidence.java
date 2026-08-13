package kg.edu.nagisa.rootsight.tool.evidence;

/**
 * HTTP 指标证据。百分比字段使用 0 到 100 的数值，便于模型直接理解。
 */
public record HttpMetricsEvidence(
        String evidenceSource,
        String targetService,
        double requestsPerSecond,
        long p95LatencyMs,
        double successRatePercent,
        String status
) {
}

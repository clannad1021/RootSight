package kg.edu.nagisa.rootsight.tool.evidence;

import java.time.Instant;

/**
 * Prometheus 指标查询的结构化证据；缺失的时间序列使用 null，避免用零值伪造观测结果。
 */
public record PrometheusMetricsEvidence(
        String evidenceSource,
        String targetName,
        String targetService,
        String status,
        boolean available,
        long responseTimeMs,
        Instant observationTime,
        String window,
        Boolean scrapeUp,
        Double requestsPerSecond,
        Double errorRatePercent,
        Double successRatePercent,
        Double p95LatencyMs,
        Double p99LatencyMs,
        Double processCpuPercent,
        Double jvmHeapUsedBytes,
        Double jvmHeapMaxBytes,
        Double jvmLiveThreads,
        String detail
) {
}

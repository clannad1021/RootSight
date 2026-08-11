package kg.edu.nagisa.rootsight.tool.fake;

import kg.edu.nagisa.rootsight.agent.trace.ToolCallTrace;
import kg.edu.nagisa.rootsight.agent.trace.ToolCallTraceRecorder;
import kg.edu.nagisa.rootsight.tool.evidence.ApplicationLogEvidence;
import kg.edu.nagisa.rootsight.tool.evidence.ComponentHealthEvidence;
import kg.edu.nagisa.rootsight.tool.evidence.HttpMetricsEvidence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fake Tool 使用固定断言，保证演示故障证据不会在后续重构中彼此矛盾。
 */
class FakeDiagnosisToolsTests {

    private ToolCallTraceRecorder traceRecorder;

    @BeforeEach
    void setUp() {
        traceRecorder = new ToolCallTraceRecorder();
        traceRecorder.start();
    }

    @Test
    void shouldBuildConsistentCacheFailureEvidenceAndTrace() {
        FakeMetricsTool metricsTool = new FakeMetricsTool(traceRecorder);
        FakeLogTool logTool = new FakeLogTool(traceRecorder);
        FakeRedisTool redisTool = new FakeRedisTool(traceRecorder);

        HttpMetricsEvidence metrics = metricsTool.queryServiceHttpMetrics("order-service");
        ApplicationLogEvidence logs = logTool.queryRecentErrorLogs("order-service");
        ComponentHealthEvidence redis = redisTool.checkRedisHealth();

        assertThat(metrics.p95LatencyMs()).isEqualTo(920);
        assertThat(metrics.successRatePercent()).isEqualTo(99.8);
        assertThat(logs.errorLogs()).anyMatch(log -> log.contains("Redis connection timeout"));
        assertThat(redis.status()).isEqualTo("DOWN");
        assertThat(redis.reachable()).isFalse();

        List<String> toolNames = traceRecorder.snapshot().stream()
                .map(ToolCallTrace::toolName)
                .toList();
        assertThat(toolNames).containsExactly(
                "query_service_http_metrics",
                "query_recent_error_logs",
                "check_redis_health"
        );
    }
}

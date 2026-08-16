package kg.edu.nagisa.rootsight.infrastructure.prometheus;

import kg.edu.nagisa.rootsight.common.constant.ExceptionMessages;
import kg.edu.nagisa.rootsight.config.InfrastructureTargetProperties;
import kg.edu.nagisa.rootsight.config.PrometheusProperties;
import kg.edu.nagisa.rootsight.tool.evidence.PrometheusMetricsEvidence;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 验证 Prometheus 固定查询、参数边界、响应解析和安全失败证据。
 */
class PrometheusMetricsClientTests {

    private static final InfrastructureTargetProperties TARGET =
            new InfrastructureTargetProperties("test-target");

    /**
     * 验证固定的九项指标会在同一时刻查询并转换为可直接诊断的结构化证据。
     */
    @Test
    void shouldQueryFixedMetricsAndReturnRealEvidence() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://prometheus.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        double[] values = {1, 12.5, 2, 150, 240, 4.5, 1000, 2000, 18};
        for (double value : values) {
            server.expect(prometheusQueryRequest())
                    .andRespond(withSuccess(vectorResponse(value), MediaType.APPLICATION_JSON));
        }
        PrometheusMetricsClient client = new PrometheusMetricsClient(builder.build(), properties(), TARGET);

        PrometheusMetricsEvidence evidence = client.queryMetrics(
                "order-service", "2026-08-16T09:00:00+08:00", "5m"
        );

        server.verify();
        assertThat(evidence.evidenceSource()).isEqualTo("REAL");
        assertThat(evidence.status()).isEqualTo("UP");
        assertThat(evidence.available()).isTrue();
        assertThat(evidence.scrapeUp()).isTrue();
        assertThat(evidence.requestsPerSecond()).isEqualTo(12.5);
        assertThat(evidence.errorRatePercent()).isEqualTo(2);
        assertThat(evidence.successRatePercent()).isEqualTo(98);
        assertThat(evidence.p95LatencyMs()).isEqualTo(150);
        assertThat(evidence.observationTime()).hasToString("2026-08-16T01:00:00Z");
    }

    /**
     * 验证非白名单窗口在发送 HTTP 请求前被拒绝，且可疑内容不会进入返回证据。
     */
    @Test
    void shouldRejectUnsafeWindowWithoutCallingPrometheus() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://prometheus.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PrometheusMetricsClient client = new PrometheusMetricsClient(builder.build(), properties(), TARGET);

        PrometheusMetricsEvidence evidence = client.queryMetrics(
                "order-service", null, "5m] offset 7d"
        );

        server.verify();
        assertThat(evidence.status()).isEqualTo("INVALID_REQUEST");
        assertThat(evidence.available()).isFalse();
        assertThat(evidence.detail()).isEqualTo(ExceptionMessages.PROMETHEUS_QUERY_INVALID);
        assertThat(evidence.toString()).doesNotContain("offset 7d");
    }

    /**
     * 验证目标序列不存在时返回 NO_DATA，并停止执行其余八条无效指标查询。
     */
    @Test
    void shouldDistinguishMissingSeriesFromServiceDown() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://prometheus.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(prometheusQueryRequest())
                .andRespond(withSuccess(emptyVectorResponse(), MediaType.APPLICATION_JSON));
        PrometheusMetricsClient client = new PrometheusMetricsClient(builder.build(), properties(), TARGET);

        PrometheusMetricsEvidence evidence = client.queryMetrics("missing-service", null, null);

        server.verify();
        assertThat(evidence.status()).isEqualTo("NO_DATA");
        assertThat(evidence.available()).isTrue();
        assertThat(evidence.scrapeUp()).isNull();
        assertThat(evidence.requestsPerSecond()).isNull();
    }

    /**
     * 验证 Prometheus HTTP 故障会返回脱敏 UNAVAILABLE 证据，而不是泄露底层请求。
     */
    @Test
    void shouldReturnUnavailableEvidenceWhenPrometheusFails() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://secret-prometheus.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(prometheusQueryRequest()).andRespond(withServerError());
        PrometheusMetricsClient client = new PrometheusMetricsClient(builder.build(), properties(), TARGET);

        PrometheusMetricsEvidence evidence = client.queryMetrics("order-service", null, null);

        server.verify();
        assertThat(evidence.status()).isEqualTo("UNAVAILABLE");
        assertThat(evidence.available()).isFalse();
        assertThat(evidence.detail()).isEqualTo(ExceptionMessages.PROMETHEUS_QUERY_FAILED);
        assertThat(evidence.toString()).doesNotContain("secret-prometheus", "/api/v1/query");
    }

    /**
     * 匹配固定的 Prometheus 瞬时查询 API，并验证统一时刻与超时参数存在。
     */
    private static RequestMatcher prometheusQueryRequest() {
        return request -> {
            assertThat(request.getURI().getPath()).isEqualTo("/api/v1/query");
            assertThat(request.getURI().getRawQuery()).contains("query=", "time=", "timeout=5s");
        };
    }

    /**
     * 创建包含单条向量样本的 Prometheus 成功响应。
     */
    private static String vectorResponse(double value) {
        return """
                {"status":"success","data":{"resultType":"vector","result":[
                  {"metric":{"application":"order-service"},"value":[1786842000,"%s"]}
                ]}}
                """.formatted(value);
    }

    /**
     * 创建没有目标时间序列的 Prometheus 成功响应。
     */
    private static String emptyVectorResponse() {
        return "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[]}}";
    }

    /**
     * 创建测试使用的 Prometheus 安全查询配置。
     */
    private static PrometheusProperties properties() {
        return new PrometheusProperties(
                "http://prometheus.test", "application", "observed-target", "5m",
                List.of("1m", "5m", "15m", "30m", "1h"), 120,
                Duration.ofSeconds(5), Duration.ofSeconds(3), Duration.ofSeconds(8)
        );
    }
}

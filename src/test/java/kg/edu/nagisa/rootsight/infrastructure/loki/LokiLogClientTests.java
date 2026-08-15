package kg.edu.nagisa.rootsight.infrastructure.loki;

import kg.edu.nagisa.rootsight.common.constant.ExceptionMessages;
import kg.edu.nagisa.rootsight.config.InfrastructureTargetProperties;
import kg.edu.nagisa.rootsight.config.LokiProperties;
import kg.edu.nagisa.rootsight.tool.evidence.LokiLogEvidence;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 验证 Loki 查询的渐进扩窗、日志脱敏、上下文补查和安全失败边界。
 */
class LokiLogClientTests {

    private static final InfrastructureTargetProperties TARGET =
            new InfrastructureTargetProperties("test-target");

    /**
     * 验证初始窗口为空时扩大到两小时，并围绕命中的异常补查 INFO 上下文。
     */
    @Test
    void shouldExpandRangeAndSanitizeLogsBeforeReturningEvidence() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://loki.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(lokiRangeRequest()).andRespond(withSuccess(emptyResponse(), MediaType.APPLICATION_JSON));
        server.expect(lokiRangeRequest()).andRespond(withSuccess(
                streamResponse("2026-08-14T01:55:00Z",
                        "ERROR password=super-secret {\"apiKey\":\"json-secret\"} downstream timeout"),
                MediaType.APPLICATION_JSON
        ));
        server.expect(lokiRangeRequest()).andRespond(withSuccess(
                streamResponse("2026-08-14T01:54:50Z",
                        "INFO Authorization: Bearer abc.def.ghi refresh started"),
                MediaType.APPLICATION_JSON
        ));
        LokiLogClient client = new LokiLogClient(builder.build(), properties(), TARGET);

        LokiLogEvidence evidence = client.queryLogs(
                "observed-target", "2026-08-14T10:00:00+08:00",
                "timeout", null, 20
        );

        assertThat(evidence.evidenceSource()).isEqualTo("REAL");
        assertThat(evidence.status()).isEqualTo("AVAILABLE");
        server.verify();
        assertThat(evidence.strategy()).isEqualTo("EXPANDED_RANGE");
        assertThat(evidence.queryAttempts()).isEqualTo(2);
        assertThat(evidence.matchedCount()).isEqualTo(1);
        assertThat(evidence.logs().get(0).message())
                .contains("password=[REDACTED]")
                .doesNotContain("super-secret", "json-secret");
        assertThat(evidence.contextAvailable()).isTrue();
        assertThat(evidence.contextLogs().get(0).message()).doesNotContain("abc.def.ghi");
    }

    /**
     * 验证包含控制字符的过滤条件在发送 HTTP 请求前就被拒绝。
     */
    @Test
    void shouldRejectUnsafeFilterWithoutCallingLoki() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://loki.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LokiLogClient client = new LokiLogClient(builder.build(), properties(), TARGET);

        LokiLogEvidence evidence = client.queryLogs(
                "observed-target", null, "timeout\n{job=\"other\"}", null, 20
        );

        server.verify();
        assertThat(evidence.status()).isEqualTo("INVALID_REQUEST");
        assertThat(evidence.available()).isFalse();
        assertThat(evidence.detail()).isEqualTo(ExceptionMessages.LOKI_QUERY_INVALID);
        assertThat(evidence.toString()).doesNotContain("job=", "other");
    }

    /**
     * 验证 Loki HTTP 故障会返回脱敏 UNAVAILABLE 证据，而不是抛出底层异常。
     */
    @Test
    void shouldReturnUnavailableEvidenceWhenLokiFails() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://secret-loki.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(lokiRangeRequest()).andRespond(withServerError());
        LokiLogClient client = new LokiLogClient(builder.build(), properties(), TARGET);

        LokiLogEvidence evidence = client.queryLogs(
                "observed-target", "2026-08-14T10:00:00+08:00", null, null, 20
        );

        assertThat(evidence.status()).isEqualTo("UNAVAILABLE");
        server.verify();
        assertThat(evidence.available()).isFalse();
        assertThat(evidence.detail()).isEqualTo(ExceptionMessages.LOKI_QUERY_FAILED);
        assertThat(evidence.toString()).doesNotContain("secret-loki");
    }

    /**
     * 验证所有渐进窗口为空后会在七天范围内查找最近异常，并明确标记历史兜底策略。
     */
    @Test
    void shouldFallbackToRecentErrorsWithinBoundedHistory() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://loki.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        for (int attempt = 0; attempt < 4; attempt++) {
            server.expect(lokiRangeRequest()).andRespond(withSuccess(emptyResponse(), MediaType.APPLICATION_JSON));
        }
        server.expect(fallbackRangeRequest()).andRespond(withSuccess(
                streamResponse("2026-08-12T02:00:00Z", "WARN historical connection reset"),
                MediaType.APPLICATION_JSON
        ));
        server.expect(lokiRangeRequest()).andRespond(withSuccess(emptyResponse(), MediaType.APPLICATION_JSON));
        LokiLogClient client = new LokiLogClient(builder.build(), properties(), TARGET);

        LokiLogEvidence evidence = client.queryLogs(
                "observed-target", "2026-08-14T10:00:00+08:00",
                "missing-keyword", null, 20
        );

        assertThat(evidence.strategy()).isEqualTo("FALLBACK_RECENT_ERRORS");
        assertThat(evidence.queryAttempts()).isEqualTo(5);
        assertThat(evidence.logs()).singleElement()
                .satisfies(entry -> assertThat(entry.message()).contains("historical connection reset"));
        server.verify();
    }

    /**
     * 匹配所有 Loki 范围查询，并验证固定 API 路径和安全上限参数存在。
     */
    private static RequestMatcher lokiRangeRequest() {
        return request -> {
            assertThat(request.getURI().getPath()).isEqualTo("/loki/api/v1/query_range");
            assertThat(request.getURI().getRawQuery())
                    .contains("query=", "start=", "end=", "limit=", "direction=backward");
        };
    }

    /**
     * 验证历史兜底查询仍限制服务和异常级别，但会移除导致短窗口为空的普通关键词。
     */
    private static RequestMatcher fallbackRangeRequest() {
        return request -> {
            lokiRangeRequest().match(request);
            assertThat(request.getURI().getRawQuery()).doesNotContain("missing-keyword");
        };
    }

    /**
     * 返回没有任何日志流的成功 Loki 响应。
     */
    private static String emptyResponse() {
        return "{\"status\":\"success\",\"data\":{\"resultType\":\"streams\",\"result\":[]}}";
    }

    /**
     * 创建包含单条日志的 Loki streams 响应。
     */
    private static String streamResponse(String timestamp, String line) {
        long epochNanos = toEpochNanos(Instant.parse(timestamp));
        String jsonLine = line.replace("\\", "\\\\").replace("\"", "\\\"");
        return """
                {"status":"success","data":{"resultType":"streams","result":[
                  {"stream":{"service_name":"observed-target"},"values":[["%d","%s"]]}
                ]}}
                """.formatted(epochNanos, jsonLine);
    }

    /**
     * 将测试时间转换为 Loki 使用的 Unix 纳秒时间戳。
     */
    private static long toEpochNanos(Instant instant) {
        return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
    }

    /**
     * 创建测试使用的有界 Loki 查询策略。
     */
    private static LokiProperties properties() {
        return new LokiProperties(
                "http://loki.test", "service_name", "observed-target",
                50, 100, 120, 1000,
                Duration.ofMinutes(30),
                List.of(Duration.ofHours(2), Duration.ofHours(6), Duration.ofHours(24)),
                Duration.ofDays(7), Duration.ofMinutes(10), Duration.ofMinutes(20),
                Duration.ofSeconds(30), 20, Duration.ofSeconds(3), Duration.ofSeconds(8)
        );
    }
}

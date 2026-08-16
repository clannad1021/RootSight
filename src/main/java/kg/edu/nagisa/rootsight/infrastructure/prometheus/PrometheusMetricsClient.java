package kg.edu.nagisa.rootsight.infrastructure.prometheus;

import kg.edu.nagisa.rootsight.common.constant.ExceptionMessages;
import kg.edu.nagisa.rootsight.config.InfrastructureTargetProperties;
import kg.edu.nagisa.rootsight.config.PrometheusProperties;
import kg.edu.nagisa.rootsight.tool.evidence.PrometheusMetricsEvidence;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Prometheus 固定指标客户端，由后端构造受限 PromQL 并把查询结果转换为诊断证据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PrometheusMetricsClient {

    private static final String EVIDENCE_SOURCE = "REAL";
    private static final String STATUS_UP = "UP";
    private static final String STATUS_DOWN = "DOWN";
    private static final String STATUS_DEGRADED = "DEGRADED";
    private static final String STATUS_NO_DATA = "NO_DATA";
    private static final String STATUS_UNAVAILABLE = "UNAVAILABLE";
    private static final String STATUS_INVALID_REQUEST = "INVALID_REQUEST";
    private static final Pattern LABEL_NAME_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern CONTROL_CHARACTER_PATTERN = Pattern.compile("\\p{Cntrl}");

    private final RestClient prometheusRestClient;
    private final PrometheusProperties properties;
    private final InfrastructureTargetProperties targetProperties;

    /**
     * 查询目标服务在指定时刻和窗口内的可用性、HTTP、CPU 与 JVM 指标。
     *
     * @return Prometheus 不可用、无时间序列和非法条件都会转换为结构化证据
     */
    public PrometheusMetricsEvidence queryMetrics(String targetService,
                                                   String incidentTime,
                                                   String requestedWindow) {
        long startedAt = System.nanoTime();
        /*
         * resolvedService   最终确定的服务名
         * window            最终确定的统计窗口
         * observationTime   最终确定的 Prometheus 查询时间
         */
        String resolvedService;
        String window;
        Instant observationTime;
        try {
            resolvedService = normalizeService(targetService);
            window = normalizeWindow(requestedWindow);
            observationTime = normalizeObservationTime(incidentTime);
            validateServiceLabel();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return invalidEvidence(startedAt);
        }

        try {
            MetricSnapshot snapshot = querySnapshot(resolvedService, window, observationTime);
            return availableEvidence(startedAt, resolvedService, window, observationTime, snapshot);
        } catch (RuntimeException exception) {
            // 不记录 PromQL、URL 或 HTTP 响应正文，避免把运维拓扑和服务标签写入 RootSight 日志。
            log.warn("Prometheus metrics query failed: {}", exception.getClass().getSimpleName());
            return unavailableEvidence(startedAt, resolvedService, window, observationTime);
        }
    }

    /**
     * 执行源码内固定的一组瞬时查询，调用方无法增加指标名、标签条件或聚合表达式。y
     */
    private MetricSnapshot querySnapshot(String service, String window, Instant observationTime) {
        String selector = selector(service);
        Double up = queryScalar("max(up" + selector + ")", observationTime);
        if (up == null) {
            // 目标序列不存在时继续执行八条指标查询不会产生新证据，直接返回 NO_DATA 可减少无效请求。
            return new MetricSnapshot(null, null, null, null, null, null, null, null, null);
        }
        //查询每秒请求数QPS
        Double requestsPerSecond = queryScalar(
                "sum(rate(http_server_requests_seconds_count" + selector + "[" + window + "]))",
                observationTime
        );
        //构造 5xx 专用选择器
        String errorSelector = selector(service, "status=~\"5..\"");
        //查询 5xx 错误率
        Double errorRatePercent = queryScalar(
                "100 * (sum(rate(http_server_requests_seconds_count" + errorSelector
                        + "[" + window + "])) or vector(0))" //or vector(0)如果没有5xx请求，Prometheus可能没有返回对应时间序列。
                        + " / clamp_min((sum(rate(http_server_requests_seconds_count" + selector
                        + "[" + window + "])) or vector(0)), 0.000000001)",//如果总请求数为 0，不能直接做除法。限制分母最小值
                observationTime
        );
        //查询 P95 延迟
        Double p95LatencyMs = queryScalar(
                latencyQuery("0.95", selector, window), observationTime
        );
        //查询 P99 延迟
        Double p99LatencyMs = queryScalar(
                latencyQuery("0.99", selector, window), observationTime
        );
        //查询进程 CPU
        Double processCpuPercent = queryScalar(
                "100 * max(process_cpu_usage" + selector + ")", observationTime
        );
        //构造 JVM 堆内存选择器
        String heapSelector = selector(service, "area=\"heap\"");
        //查询 JVM 堆已使用内存
        Double heapUsedBytes = queryScalar(
                "sum(jvm_memory_used_bytes" + heapSelector + ")", observationTime
        );
        //查询 JVM 堆最大内存
        Double heapMaxBytes = queryScalar(
                "sum(clamp_min(jvm_memory_max_bytes" + heapSelector + ", 0))", observationTime
        );
        //查询活跃线程数
        Double liveThreads = queryScalar(
                "sum(jvm_threads_live_threads" + selector + ")", observationTime
        );
        //构造 MetricSnapshot
        return new MetricSnapshot(
                up, requestsPerSecond, errorRatePercent, p95LatencyMs, p99LatencyMs,
                processCpuPercent, heapUsedBytes, heapMaxBytes, liveThreads
        );
    }

    /**
     * 构造可跨实例聚合的服务端延迟直方图查询，并把秒转换为毫秒。y
     */
    private static String latencyQuery(String quantile, String selector, String window) {
        return "1000 * histogram_quantile(" + quantile
                + ", sum by (le) (rate(http_server_requests_seconds_bucket" //le 表示桶的上限 | HTTP 延迟直方图桶
                + selector + "[" + window + "])))";
    }

    /**
     * 调用 Prometheus 瞬时查询 API，并读取向量结果中的第一个有限数值。y
     */
    private Double queryScalar(String promQl, Instant observationTime) {
        Map<String, Object> response = prometheusRestClient.get()
                .uri(queryUri(promQl, observationTime))
                //执行请求并准备读取响应。
                .retrieve()
                //把 Prometheus 返回的 JSON 读取成：Map<String, Object>
                .body(new ParameterizedTypeReference<>() {
                });
        if (response == null || !"success".equals(response.get("status"))) {
            throw new IllegalStateException(ExceptionMessages.PROMETHEUS_QUERY_FAILED);
        }
        Map<?, ?> data = mapValue(response.get("data"));
        Object resultValue = data.get("result");
        if (!(resultValue instanceof List<?> result) || result.isEmpty()) {
            return null;
        }
        Map<?, ?> sample = mapValue(result.get(0));
        Object valueObject = sample.get("value");
        if (!(valueObject instanceof List<?> value) || value.size() < 2) {
            throw new IllegalStateException(ExceptionMessages.PROMETHEUS_QUERY_FAILED);
        }
        double parsed = Double.parseDouble(String.valueOf(value.get(1)));
        return Double.isFinite(parsed) ? parsed : null;
    }

    /**
     * 将后端生成的 PromQL、统一观测时刻和查询超时编码为固定 API 请求。y
     */
    private URI queryUri(String promQl, Instant observationTime) {
        long timeoutSeconds = Math.max(1, properties.queryTimeout().toSeconds());
        return UriComponentsBuilder.fromPath("/api/v1/query")
                .queryParam("query", promQl)
                .queryParam("time", observationTime.toString())
                .queryParam("timeout", timeoutSeconds + "s")
                .build()
                .encode()
                .toUri();
    }

    /**
     * 使用运维配置的固定标签名和经过转义的标签值构造精确序列选择器。y
     */
    private String selector(String service) {
        return "{" + properties.serviceLabel() + "=\"" + escapeLabelValue(service) + "\"}";
    }

    /**
     * 在固定服务标签之外追加一个由源码定义的匹配器，调用方仍不能改变查询结构。y
     */
    private String selector(String service, String fixedMatcher) {
        return "{" + properties.serviceLabel() + "=\"" + escapeLabelValue(service)
                + "\"," + fixedMatcher + "}";
    }

    /**
     * 校验配置的标签名只能使用 Prometheus 合法标识符，避免配置错误改变查询结构。y
     */
    private void validateServiceLabel() {
        if (!StringUtils.hasText(properties.serviceLabel())
                || !LABEL_NAME_PATTERN.matcher(properties.serviceLabel()).matches()) {
            throw new IllegalStateException(ExceptionMessages.PROMETHEUS_QUERY_INVALID);
        }
    }

    /**
     * 规范服务名并限制长度与控制字符，随后仍会对 PromQL 标签字符串进行转义。y
     */
    private String normalizeService(String targetService) {
        String resolved = StringUtils.hasText(targetService)
                ? targetService.trim()
                : properties.defaultService();
        if (!StringUtils.hasText(resolved)
                || resolved.length() > Math.max(1, properties.maxServiceLength()) //大于最大服务名长度
                || CONTROL_CHARACTER_PATTERN.matcher(resolved).find()) {
            throw new IllegalArgumentException(ExceptionMessages.PROMETHEUS_QUERY_INVALID);
        }
        return resolved;
    }

    /**
     * 只接受运维配置的窗口白名单，防止超长范围和 PromQL 片段进入固定查询。y
     */
    private String normalizeWindow(String requestedWindow) {
        String resolved = StringUtils.hasText(requestedWindow)
                ? requestedWindow.trim()
                : properties.defaultWindow();
        if (!StringUtils.hasText(resolved)
                || properties.allowedWindows() == null
                || !properties.allowedWindows().contains(resolved)) {
            throw new IllegalArgumentException(ExceptionMessages.PROMETHEUS_QUERY_INVALID);
        }
        return resolved;
    }

    /**
     * 解析带时区的 ISO-8601 故障时间；未提供时使用当前时刻作为全部查询的统一锚点。y
     */
    private static Instant normalizeObservationTime(String incidentTime) {
        if (!StringUtils.hasText(incidentTime)) {
            return Instant.now();
        }
        try {
            return Instant.parse(incidentTime.trim());
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(incidentTime.trim()).toInstant();
            } catch (DateTimeParseException exception) {
                throw new IllegalArgumentException(ExceptionMessages.PROMETHEUS_QUERY_INVALID, exception);
            }
        }
    }

    /**
     * 转义 PromQL 标签值中的反斜杠和双引号，保持值始终位于同一标签匹配器内。y
     */
    private static String escapeLabelValue(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 根据 up 和核心 HTTP 序列是否存在构造可用、宕机、部分指标或无数据证据。y
     */
    private PrometheusMetricsEvidence availableEvidence(long startedAt,
                                                         String service,
                                                         String window,
                                                         Instant observationTime,
                                                         MetricSnapshot snapshot) {
        /*
         * 计算 scrapeUp
         * up == null → scrapeUp = null
         * up == 0    → scrapeUp = false
         * up > 0     → scrapeUp = true
         */
        Boolean scrapeUp = snapshot.up() == null ? null : snapshot.up() > 0;
        String status;
        String detail;
        if (scrapeUp == null) {
            status = STATUS_NO_DATA;
            detail = "Prometheus 查询成功，但没有找到目标服务的时间序列";
        } else if (!scrapeUp) {
            status = STATUS_DOWN;
            detail = "Prometheus 已记录目标服务抓取失败";
        } else if (snapshot.requestsPerSecond() == null) {
            status = STATUS_DEGRADED;
            detail = "目标服务抓取正常，但 HTTP 请求指标尚不可用";
        } else {
            status = STATUS_UP;
            detail = "Prometheus 固定只读指标查询成功";
        }
        //计算成功率
        Double successRate = snapshot.errorRatePercent() == null
                ? null
                : Math.max(0, Math.min(100, 100 - snapshot.errorRatePercent()));
        return new PrometheusMetricsEvidence(
                EVIDENCE_SOURCE, targetProperties.name(), service, status, true,
                //计算查询耗时
                elapsedMillis(startedAt), observationTime, window, scrapeUp,
                snapshot.requestsPerSecond(), snapshot.errorRatePercent(), successRate,
                snapshot.p95LatencyMs(), snapshot.p99LatencyMs(), snapshot.processCpuPercent(),
                snapshot.heapUsedBytes(), snapshot.heapMaxBytes(), snapshot.liveThreads(), detail
        );
    }

    /**
     * 构造非法查询条件证据，不回显调用方提供的可疑服务名、时间或窗口。y
     */
    private PrometheusMetricsEvidence invalidEvidence(long startedAt) {
        return new PrometheusMetricsEvidence(
                EVIDENCE_SOURCE, targetProperties.name(), properties.defaultService(),
                STATUS_INVALID_REQUEST, false, elapsedMillis(startedAt), null,
                properties.defaultWindow(), null, null, null, null, null, null,
                null, null, null, null, ExceptionMessages.PROMETHEUS_QUERY_INVALID
        );
    }

    /**
     * 构造脱敏的 Prometheus 不可用证据，不返回 URL、PromQL 或底层响应正文。y
     */
    private PrometheusMetricsEvidence unavailableEvidence(long startedAt,
                                                           String service,
                                                           String window,
                                                           Instant observationTime) {
        return new PrometheusMetricsEvidence(
                EVIDENCE_SOURCE, targetProperties.name(), service, STATUS_UNAVAILABLE,
                false, elapsedMillis(startedAt), observationTime, window,
                null, null, null, null, null, null, null, null, null, null,
                ExceptionMessages.PROMETHEUS_QUERY_FAILED
        );
    }

    /**
     * 将动态 JSON 字段安全转换为 Map，类型不符时视为 Prometheus 协议异常。y
     */
    private static Map<?, ?> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        throw new IllegalStateException(ExceptionMessages.PROMETHEUS_QUERY_FAILED);
    }

    /**
     * 计算从指定单调时钟起点到当前时刻的毫秒耗时。y
     */
    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    /**
     * 保存同一观测时刻的一组原始指标值，供统一状态判断和证据组装使用。y
     */
    private record MetricSnapshot(
            Double up,
            Double requestsPerSecond,
            Double errorRatePercent,
            Double p95LatencyMs,
            Double p99LatencyMs,
            Double processCpuPercent,
            Double heapUsedBytes,
            Double heapMaxBytes,
            Double liveThreads
    ) {
    }
}

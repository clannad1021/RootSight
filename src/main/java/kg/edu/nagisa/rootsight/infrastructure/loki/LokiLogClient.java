package kg.edu.nagisa.rootsight.infrastructure.loki;

import kg.edu.nagisa.rootsight.common.constant.ExceptionMessages;
import kg.edu.nagisa.rootsight.config.InfrastructureTargetProperties;
import kg.edu.nagisa.rootsight.config.LokiProperties;
import kg.edu.nagisa.rootsight.tool.evidence.LogTimeRangeEvidence;
import kg.edu.nagisa.rootsight.tool.evidence.LokiLogEntryEvidence;
import kg.edu.nagisa.rootsight.tool.evidence.LokiLogEvidence;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Loki 只读日志客户端，由后端根据结构化条件构造受限 LogQL，并执行有界渐进扩窗查询。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LokiLogClient {

    private static final String EVIDENCE_SOURCE = "REAL";
    private static final String STATUS_AVAILABLE = "AVAILABLE";
    private static final String STATUS_UNAVAILABLE = "UNAVAILABLE";
    private static final String STATUS_INVALID_REQUEST = "INVALID_REQUEST";
    private static final Pattern LABEL_NAME_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern CONTROL_CHARACTER_PATTERN = Pattern.compile("[\\p{Cntrl}&&[^\\t]]");
    private static final Pattern SENSITIVE_ASSIGNMENT_PATTERN = Pattern.compile(
            "(?i)([\"']?\\b(?:password|passwd|api[_-]?key|authorization|access[_-]?token|refresh[_-]?token)"
                    + "[\"']?\\s*[:=]\\s*)(?:\"[^\"]*\"|'[^']*'|[^\\s,;]+)"
    );
    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/-]+=*");

    private final RestClient lokiRestClient;
    private final LokiProperties properties;
    private final InfrastructureTargetProperties targetProperties;

    /**
     * 按目标服务、故障时间和可选文本条件查询 ERROR/WARN 日志，并在空结果时按配置逐步扩大范围。
     *
     * @return Loki 可用、不可用和非法条件均转换为结构化证据，不向模型抛出底层 HTTP 异常
     */
    public LokiLogEvidence queryLogs(String targetService,
                                     String incidentTime,
                                     String keyword,
                                     String traceId,
                                     Integer requestedLimit) {
        long startedAt = System.nanoTime();
        Instant now = Instant.now();
        /*
         * resolvedService     最终确定的服务名
         * requestedRange      用户要求的查询时间范围
         * limit               最终返回数量
         * normalizedKeyword   校验后的关键词
         * normalizedTraceId   校验后的 traceId
         */
        String resolvedService;
        LogTimeRangeEvidence requestedRange;
        int limit;
        String normalizedKeyword;
        String normalizedTraceId;
        try {
            resolvedService = normalizeFilter(targetService, properties.defaultService(), "targetService");
            normalizedKeyword = normalizeOptionalFilter(keyword, "keyword");
            normalizedTraceId = normalizeOptionalFilter(traceId, "traceId");
            limit = normalizeLimit(requestedLimit);
            requestedRange = requestedRange(incidentTime, now);
        } catch (IllegalArgumentException exception) {
            return invalidEvidence(startedAt);
        }
        try {
            return executeSearch(
                    lokiRestClient, resolvedService, normalizedKeyword, normalizedTraceId,
                    requestedRange, limit, startedAt
            );
        } catch (RuntimeException exception) {
            // 只记录异常类型，避免 Loki URL、LogQL 或服务日志进入 RootSight 自身日志。
            log.warn("Loki log query failed: {}", exception.getClass().getSimpleName());
            return unavailableEvidence(startedAt, resolvedService, requestedRange);
        }
    }

    /**
     * 执行精确窗口、渐进扩窗和七天内最近异常兜底，并在命中后补查异常附近上下文。
     */
    private LokiLogEvidence executeSearch(RestClient restClient,
                                          String targetService,
                                          String keyword,
                                          String traceId,
                                          LogTimeRangeEvidence requestedRange,
                                          int limit,
                                          long startedAt) {
        String errorQuery = buildErrorQuery(targetService, keyword, traceId);
        List<LogTimeRangeEvidence> ranges = expansionRanges(requestedRange);
        int attempts = 0;

        for (int index = 0; index < ranges.size(); index++) {
            LogTimeRangeEvidence range = ranges.get(index);
            attempts++;
            LokiQueryResult result = queryRange(restClient, errorQuery, range, limit);
            if (!result.entries().isEmpty()) {
                String strategy = index == 0 ? "EXACT_RANGE" : "EXPANDED_RANGE";
                return successEvidence(
                        restClient, targetService, traceId, requestedRange, range,
                        strategy, attempts, result, limit, startedAt
                );
            }
        }

        LogTimeRangeEvidence lastRange = ranges.get(ranges.size() - 1);
        LogTimeRangeEvidence fallbackRange = new LogTimeRangeEvidence(
                requestedRange.end().minus(properties.fallbackWindow()), requestedRange.end()
        );
        if (fallbackRange.start().isBefore(lastRange.start())) {
            attempts++;
            // 历史兜底移除普通关键词，避免过窄条件把该服务最近的其他异常也全部过滤掉；traceId 仍保持精确约束。
            String fallbackQuery = buildErrorQuery(targetService, null, traceId);
            LokiQueryResult fallbackResult = queryRange(restClient, fallbackQuery, fallbackRange, limit);
            if (!fallbackResult.entries().isEmpty()) {
                return successEvidence(
                        restClient, targetService, traceId, requestedRange, fallbackRange,
                        "FALLBACK_RECENT_ERRORS", attempts, fallbackResult, limit, startedAt
                );
            }
            lastRange = fallbackRange;
        }

        return new LokiLogEvidence(
                EVIDENCE_SOURCE,
                targetProperties.name(),
                targetService,
                STATUS_AVAILABLE,
                true,
                elapsedMillis(startedAt),
                requestedRange,
                lastRange,
                "NO_MATCH",
                attempts,
                0,
                false,
                null,
                List.of(),
                true,
                List.of(),
                "在最大允许回溯范围内未发现符合条件的 ERROR/WARN 日志"
        );
    }

    /**
     * 组合成功查询证据，并把上下文补查视为可选增强而不是主查询的成败条件。y
     */
    private LokiLogEvidence successEvidence(RestClient restClient,
                                             String targetService,
                                             String traceId,
                                             LogTimeRangeEvidence requestedRange,
                                             LogTimeRangeEvidence effectiveRange,
                                             String strategy,
                                             int attempts,
                                             LokiQueryResult result,
                                             int limit,
                                             long startedAt) {
        ContextResult contextResult = queryContextSafely(restClient, targetService, traceId, result.entries().get(0));
        boolean truncated = result.entries().size() >= limit; //如果返回数量达到上限，就认为可能还有更多日志
        return new LokiLogEvidence(
                EVIDENCE_SOURCE,
                targetProperties.name(),
                targetService,
                STATUS_AVAILABLE,
                true,
                elapsedMillis(startedAt),
                requestedRange,
                effectiveRange,
                strategy,
                attempts,
                result.entries().size(),
                truncated,
                truncated ? nextCursor(result.entries()) : null,
                result.entries(),
                contextResult.available(),
                contextResult.entries(),
                contextResult.available()
                        ? "Loki 异常日志和命中时间附近上下文查询成功"
                        : "Loki 异常日志查询成功，但上下文补查不可用"
        );
    }

    /**
     * 在最新异常前后各查询一个短窗口的日志，为 ERROR/WARN 补充可能的前置 INFO 事件。y
     */
    private ContextResult queryContextSafely(RestClient restClient,
                                              String targetService,
                                              String traceId,
                                              LokiLogEntryEvidence anchor) {
        try {
            LogTimeRangeEvidence contextRange = new LogTimeRangeEvidence(
                    anchor.timestamp().minus(properties.contextWindow()),
                    anchor.timestamp().plus(properties.contextWindow())
            );
            int contextLimit = Math.max(1, Math.min(properties.contextLimit(), properties.maxLimit()));
            String contextQuery = buildContextQuery(targetService, traceId);
            return new ContextResult(true, queryRange(restClient, contextQuery, contextRange, contextLimit).entries());
        } catch (RuntimeException exception) {
            log.info("Loki context query is unavailable: {}", exception.getClass().getSimpleName());
            return new ContextResult(false, List.of());
        }
    }

    /**
     * 调用 Loki query_range API，并把 streams 响应扁平化为按时间倒序排列的日志证据。y
     */
    private LokiQueryResult queryRange(RestClient restClient,
                                       String logQuery,
                                       LogTimeRangeEvidence range,
                                       int limit) {
        Map<String, Object> response = restClient.get()
                .uri(queryRangeUri(logQuery, range, limit))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        if (response == null || !"success".equals(response.get("status"))) {
            throw new IllegalStateException(ExceptionMessages.LOKI_QUERY_FAILED);
        }
        return new LokiQueryResult(readEntries(response));
    }

    /**
     * 将后端生成的 LogQL 作为普通查询参数编码，防止花括号被误识别成 URI 模板变量。y
     */
    private static URI queryRangeUri(String logQuery, LogTimeRangeEvidence range, int limit) {
        return UriComponentsBuilder.fromPath("/loki/api/v1/query_range")
                .queryParam("query", logQuery)
                .queryParam("start", toEpochNanos(range.start()))
                .queryParam("end", toEpochNanos(range.end()))
                .queryParam("limit", limit)
                .queryParam("direction", "backward")
                .build()
                .encode()
                .toUri();
    }

    /**
     * 从 Loki data.result 流数组读取时间戳和日志正文，不向证据暴露其他任意标签。y
     */
    private List<LokiLogEntryEvidence> readEntries(Map<String, Object> response) {
        Map<?, ?> data = mapValue(response.get("data"));
        Object resultValue = data.get("result");
        if (!(resultValue instanceof List<?> streams)) {
            return List.of();
        }

        List<LokiLogEntryEvidence> entries = new ArrayList<>();
        for (Object streamValue : streams) {
            Map<?, ?> stream = mapValue(streamValue);
            Object valuesValue = stream.get("values");
            if (!(valuesValue instanceof List<?> values)) {
                continue;
            }
            for (Object pairValue : values) {
                if (pairValue instanceof List<?> pair && pair.size() >= 2) {
                    entries.add(toEntry(pair));
                }
            }
        }
        return entries.stream() //时间倒序排列
                .sorted(Comparator.comparing(LokiLogEntryEvidence::timestamp).reversed())
                .toList();
    }

    /**
     * 将 Loki 的纳秒时间戳和日志行转换为脱敏、限长的单条证据。y
     */
    private LokiLogEntryEvidence toEntry(List<?> pair) {
        Instant timestamp = fromEpochNanos(String.valueOf(pair.get(0)));
        String sanitizedMessage = sanitizeLogLine(String.valueOf(pair.get(1)));
        return new LokiLogEntryEvidence(timestamp, inferLevel(sanitizedMessage), sanitizedMessage);
    }

    /**
     * 根据调用方是否提供故障时间，计算围绕故障点或截至当前时刻的初始查询范围。y
     */
    private LogTimeRangeEvidence requestedRange(String incidentTime, Instant now) {
        if (!StringUtils.hasText(incidentTime)) {
            return new LogTimeRangeEvidence(now.minus(properties.defaultWindow()), now);
        }
        Instant incident = parseIncidentTime(incidentTime.trim());
        if (incident.isAfter(now.plus(properties.incidentAfter()))) {
            throw new IllegalArgumentException(ExceptionMessages.LOKI_QUERY_INVALID);
        }
        Instant end = incident.plus(properties.incidentAfter());
        if (end.isAfter(now)) {
            end = now;
        }
        Instant start = incident.minus(properties.incidentBefore());
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException(ExceptionMessages.LOKI_QUERY_INVALID);
        }
        return new LogTimeRangeEvidence(start, end);
    }

    /**
     * 解析带时区的 ISO-8601 故障时间，统一转换为 UTC Instant。y
     */
    private static Instant parseIncidentTime(String incidentTime) {
        try {
            return Instant.parse(incidentTime);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(incidentTime).toInstant();
            } catch (DateTimeParseException exception) {
                throw new IllegalArgumentException(ExceptionMessages.LOKI_QUERY_INVALID, exception);
            }
        }
    }

    /**
     * 生成去重且按时长递增的查询范围，始终保留调用方请求的原始范围作为第一次尝试。y
     */
    private List<LogTimeRangeEvidence> expansionRanges(LogTimeRangeEvidence requestedRange) {
        List<LogTimeRangeEvidence> ranges = new ArrayList<>();
        ranges.add(requestedRange);
        Duration requestedDuration = Duration.between(requestedRange.start(), requestedRange.end());
        Set<Duration> windows = new LinkedHashSet<>(properties.expansionWindows());
        windows.stream()
                .filter(window -> !window.isNegative() && !window.isZero())
                .filter(window -> window.compareTo(requestedDuration) > 0)
                .filter(window -> window.compareTo(properties.fallbackWindow()) < 0)
                .sorted()
                .map(window -> new LogTimeRangeEvidence(requestedRange.end().minus(window), requestedRange.end()))
                .forEach(ranges::add);
        return ranges;
    }

    /**
     * 构造只包含固定服务标签、ERROR/WARN 和可选文本条件的 LogQL。y
     */
    private String buildErrorQuery(String targetService, String keyword, String traceId) {
        return buildSelector(targetService)
                + " |~ \"(?i)(ERROR|WARN)\""
                + lineFilter(keyword)
                + lineFilter(traceId);
    }

    /**
     * 构造上下文查询；不限制日志级别，但仍限定服务标签和可选 traceId。y
     */
    private String buildContextQuery(String targetService, String traceId) {
        return buildSelector(targetService) + lineFilter(traceId);
    }

    /**
     * 使用运维配置的固定标签名和经过转义的标签值构造精确流选择器。y
     */
    private String buildSelector(String targetService) {
        if (!LABEL_NAME_PATTERN.matcher(properties.serviceLabel()).matches()) {
            throw new IllegalStateException(ExceptionMessages.LOKI_QUERY_FAILED);
        }
        return "{" + properties.serviceLabel() + "=\"" + escapeLogQlString(targetService) + "\"}";
    }

    /**
     * 将可选文本条件追加为精确包含过滤器，空值不会产生额外 LogQL 片段。y
     */
    private static String lineFilter(String value) {
        return value == null ? "" : " |= \"" + escapeLogQlString(value) + "\"";
    }

    /**
     * 规范必填过滤值；未提供目标服务时使用运维侧配置的默认服务标签值。y
     */
    private String normalizeFilter(String value, String defaultValue, String fieldName) {
        String resolved = StringUtils.hasText(value) ? value.trim() : defaultValue;
        if (!StringUtils.hasText(resolved)) {
            throw new IllegalArgumentException(fieldName);
        }
        validateFilter(resolved);
        return resolved;
    }

    /**
     * 规范可选过滤值，空白值按未提供处理。y
     */
    private String normalizeOptionalFilter(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String resolved = value.trim();
        try {
            validateFilter(resolved);
            return resolved;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(fieldName, exception);
        }
    }

    /**
     * 拒绝超长值和控制字符，再由转义方法处理引号与反斜杠，阻止 LogQL 结构注入。y
     */
    private void validateFilter(String value) {
        if (value.length() > properties.maxFilterLength()
                || CONTROL_CHARACTER_PATTERN.matcher(value).find()) {
            throw new IllegalArgumentException(ExceptionMessages.LOKI_QUERY_INVALID);
        }
    }

    /**
     * 转义 LogQL 双引号字符串中的反斜杠和引号。y
     *
     */
    private static String escapeLogQlString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 把请求数量限制在 1 到运维配置上限之间，未提供时使用默认值。y
     */
    private int normalizeLimit(Integer requestedLimit) {
        int maximum = Math.max(1, properties.maxLimit());
        int fallback = Math.max(1, Math.min(properties.defaultLimit(), maximum));
        return requestedLimit == null ? fallback : Math.max(1, Math.min(requestedLimit, maximum));
    }

    /**
     * 脱敏常见凭证形式并限制单行长度，降低日志内容进入模型上下文的泄露和成本风险。y
     */
    private String sanitizeLogLine(String line) {
        String sanitized = BEARER_TOKEN_PATTERN.matcher(line).replaceAll("Bearer [REDACTED]");
        sanitized = SENSITIVE_ASSIGNMENT_PATTERN.matcher(sanitized).replaceAll("$1[REDACTED]");
        int maximumLength = Math.max(20, properties.maxLineLength());
        return sanitized.length() <= maximumLength
                ? sanitized
                : sanitized.substring(0, maximumLength) + "...[TRUNCATED]";
    }

    /**
     * 从日志正文推断常见级别；无法判断时标记 UNKNOWN，避免编造日志标签。y
     */
    private static String inferLevel(String line) {
        String upper = line.toUpperCase();
        if (upper.contains("ERROR")) {
            return "ERROR";
        }
        if (upper.contains("WARN")) {
            return "WARN";
        }
        if (upper.contains("INFO")) {
            return "INFO";
        }
        if (upper.contains("DEBUG")) {
            return "DEBUG";
        }
        return "UNKNOWN";
    }

    /**
     * 将 Loki JSON 中的对象字段安全转换为 Map，类型不符时视为协议异常。y
     */
    private static Map<?, ?> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        throw new IllegalStateException(ExceptionMessages.LOKI_QUERY_FAILED);
    }

    /**
     * 将 Instant 转换为 Loki API 接受的 Unix 纳秒时间戳。y
     */
    private static long toEpochNanos(Instant instant) {
        return Math.addExact(Math.multiplyExact(instant.getEpochSecond(), 1_000_000_000L), instant.getNano());
    }

    /**
     * 将 Loki 返回的 Unix 纳秒时间戳转换为 Instant。y
     */
    private static Instant fromEpochNanos(String value) {
        long epochNanos = Long.parseLong(value);
        return Instant.ofEpochSecond(
                Math.floorDiv(epochNanos, 1_000_000_000L),
                Math.floorMod(epochNanos, 1_000_000_000L)
        );
    }

    /**
     * 生成倒序分页的下一游标，下一页应从当前最早日志之前一纳秒继续。y
     */
    private static String nextCursor(List<LokiLogEntryEvidence> entries) {
        Instant oldest = entries.stream()
                .map(LokiLogEntryEvidence::timestamp)
                .min(Instant::compareTo)
                .orElseThrow();
        return String.valueOf(toEpochNanos(oldest.minusNanos(1)));
    }

    /**
     * 构造非法查询条件证据，不回显用户提供的可疑过滤内容。y
     */
    private LokiLogEvidence invalidEvidence(long startedAt) {
        return new LokiLogEvidence(
                EVIDENCE_SOURCE, targetProperties.name(), properties.defaultService(),
                STATUS_INVALID_REQUEST, false, elapsedMillis(startedAt),
                null, null, "REJECTED", 0, 0, false, null,
                List.of(), false, List.of(), ExceptionMessages.LOKI_QUERY_INVALID
        );
    }

    /**
     * 构造脱敏的 Loki 不可用证据，不返回 URL、LogQL 或底层响应正文。y
     */
    private LokiLogEvidence unavailableEvidence(long startedAt,
                                                 String targetService,
                                                 LogTimeRangeEvidence requestedRange) {
        return new LokiLogEvidence(
                EVIDENCE_SOURCE, targetProperties.name(), targetService,
                STATUS_UNAVAILABLE, false, elapsedMillis(startedAt),
                requestedRange, null, "LOKI_UNAVAILABLE", 0, 0, false, null,
                List.of(), false, List.of(), ExceptionMessages.LOKI_QUERY_FAILED
        );
    }

    /**
     * 计算从指定单调时钟起点到当前时刻的毫秒耗时。y
     */
    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    /**
     * 保存一次 Loki 范围查询已经排序和脱敏的日志条目。y
     */
    private record LokiQueryResult(List<LokiLogEntryEvidence> entries) {
    }

    /**
     * 区分上下文查询成功但为空和上下文查询本身不可用两种情况。y
     */
    private record ContextResult(boolean available, List<LokiLogEntryEvidence> entries) {
    }
}

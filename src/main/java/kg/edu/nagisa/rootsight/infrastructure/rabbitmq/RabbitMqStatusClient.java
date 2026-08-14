package kg.edu.nagisa.rootsight.infrastructure.rabbitmq;

import kg.edu.nagisa.rootsight.common.constant.ExceptionMessages;
import kg.edu.nagisa.rootsight.config.InfrastructureTargetProperties;
import kg.edu.nagisa.rootsight.config.RabbitMqManagementProperties;
import kg.edu.nagisa.rootsight.tool.evidence.RabbitMqQueueEvidence;
import kg.edu.nagisa.rootsight.tool.evidence.RabbitMqStatusEvidence;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * RabbitMQ 状态客户端，只读取 Management HTTP API，不建立 AMQP 连接，也不消费或发布消息。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMqStatusClient {

    private static final String EVIDENCE_SOURCE = "REAL";
    private static final String COMPONENT = "rabbitmq";
    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 500;

    private final RestClient rabbitMqManagementRestClient;
    private final RabbitMqManagementProperties properties;
    private final InfrastructureTargetProperties targetProperties;

    /**
     * 查询 RabbitMQ 概览和指定 vhost 的有界队列状态，并转换为可供 Agent 分析的结构化证据。
     *
     * @return 成功或失败都返回脱敏证据，目标故障不会中断整次诊断
     */
    public RabbitMqStatusEvidence inspectStatus() {
        long startedAt = System.nanoTime();
        try {
            Map<String, Object> overview = getObject("/api/overview");
            Map<String, Object> queuePage = getObject(queuePageUri());
            List<RabbitMqQueueEvidence> inspectedQueues = readQueues(queuePage);
            long totalQueueCount = longValue(queuePage.get("total_count")); //获取队列总数
            int sampleLimit = Math.max(0, properties.queueSampleLimit()); //最终返回给 Agent 多少个队列详情
            List<RabbitMqQueueEvidence> queueSample = inspectedQueues.stream()
                    /*
                     * .sorted() 是 Java Stream 的中间操作，用来排序。它需要接收一个“比较规则”
                     * Comparator 是 Java 提供的比较器接口
                     * Comparator.comparingLong() 是一个静态方法，可以根据对象中的某个 long 类型数值生成比较器
                     * 按照每个队列的 messagesUnacknowledged 数值进行比较，
                     * thenComparingLong表示第二排序条件
                     * .reversed把整个比较规则反转，Comparator.comparingLong是从小到大排序
                     * 变成从大到小排序
                     */
                    .sorted(Comparator.comparingLong(RabbitMqQueueEvidence::messagesUnacknowledged)
                            .thenComparingLong(RabbitMqQueueEvidence::messagesReady)
                            .reversed())
                    .limit(sampleLimit)
                    .toList();
            //判断结果是否被截断
            boolean truncated = totalQueueCount > inspectedQueues.size()
                    || inspectedQueues.size() > queueSample.size();
            String status = inspectedQueues.stream()
                    .map(RabbitMqQueueEvidence::state)
                    .filter(state -> state != null && !state.isBlank())
                    /*
                     *allMatch() 的意思是：Stream 中的所有元素是否都满足条件返回true或false
                     *"running"::equalsIgnoreCase等价于state -> "running".equalsIgnoreCase(state)
                     */
                    .allMatch("running"::equalsIgnoreCase) ? "UP" : "DEGRADED";

            return new RabbitMqStatusEvidence(
                    EVIDENCE_SOURCE,
                    targetProperties.name(),
                    COMPONENT,
                    status,
                    true,
                    elapsedMillis(startedAt),
                    stringValue(overview.get("rabbitmq_version")),
                    stringValue(overview.get("cluster_name")),
                    properties.vhost(),
                    totalQueueCount,
                    inspectedQueues.size(),
                    truncated,
                    sum(inspectedQueues, RabbitMqQueueEvidence::messages),
                    sum(inspectedQueues, RabbitMqQueueEvidence::messagesReady),
                    sum(inspectedQueues, RabbitMqQueueEvidence::messagesUnacknowledged),
                    sum(inspectedQueues, RabbitMqQueueEvidence::consumers),
                    queueSample,
                    "RabbitMQ Management API 和指定 vhost 队列状态查询成功"
            );
        } catch (RuntimeException exception) {
            // 只记录异常类型，避免 Management URL、账号或响应正文进入日志和模型上下文。
            log.warn("RabbitMQ status inspection failed: {}", exception.getClass().getSimpleName());
            return downEvidence(startedAt);
        }
    }

    /**
     * 使用 RestClient 向 RabbitMQ Management API 发起 GET 请求,并把 JSON 对象转换成 Map。
     * 读取 Management API 的 JSON 对象响应，字段解析由上层按固定白名单完成。
     */
    private Map<String, Object> getObject(String uri) {
                                                            //.get() 表示准备发送 GET 请求
        Map<String, Object> body = rabbitMqManagementRestClient.get()
                //把字符串转换成URI，eg：http://127.0.0.1:15672/api/overview
                .uri(URI.create(uri))
                //执行 HTTP 请求，并准备读取响应
                .retrieve()
                //把 HTTP 响应体中的 JSON 转换成：Map<String, Object>
                .body(new ParameterizedTypeReference<>() {
                });
        if (body == null) {
            throw new IllegalStateException(ExceptionMessages.RABBITMQ_INSPECTION_FAILED);
        }
        return body;
    }

    /**
     * 构造只指向当前配置 vhost 的队列分页 URI，并把斜杠等特殊字符编码为单个路径段。
     */
    private String queuePageUri() {
        int pageSize = Math.max(MIN_PAGE_SIZE, Math.min(properties.queuePageSize(), MAX_PAGE_SIZE));
                                    //从 /api/queues 开始构建 URL
        return UriComponentsBuilder.fromPath("/api/queues")
                .pathSegment(properties.vhost()) //把当前 vhost 作为路径段加入
                //加入分页页码：page=1
                .queryParam("page", 1)
                .queryParam("page_size", pageSize)
                .build()
                .encode() //特殊字符编码，并转成字符串
                .toUriString(); //api/queues/%2F?page=1&page_size=100
    }

    /**
     * 将队列分页响应中的固定状态字段转换为证据，不读取消息正文或其他对象详情。
     */
    private static List<RabbitMqQueueEvidence> readQueues(Map<String, Object> queuePage) {
        Object items = queuePage.get("items");
        if (!(items instanceof List<?> list)) {
            return List.of();
        }           //把队列列表转换成 Stream
        return list.stream()
                //Java 的 instanceof 模式匹配（Pattern Matching）
                .filter(Map.class::isInstance)//如果 items不是List，直接返回空List。是List，就顺便创建一个叫list的List类型引用。
                //基本等价.map(x -> Map.class.cast(x))
                //cast类似强制类型转换.map(x -> (Map) x)
                .map(Map.class::cast)
                .map(RabbitMqStatusClient::toQueueEvidence)
                .toList();
    }

    /**
     * 从单个队列对象中提取固定白名单字段。
     */
    private static RabbitMqQueueEvidence toQueueEvidence(Map<?, ?> queue) {
        return new RabbitMqQueueEvidence(
                stringValue(queue.get("name")),
                stringValue(queue.get("state")),
                longValue(queue.get("messages")),
                longValue(queue.get("messages_ready")),
                longValue(queue.get("messages_unacknowledged")),
                longValue(queue.get("consumers"))
        );
    }

    /**
     * 汇总当前有界分页中的队列指标；结果不代表未读取分页的数据。
     */
    private static long sum(List<RabbitMqQueueEvidence> queues,
                            java.util.function.ToLongFunction<RabbitMqQueueEvidence> mapper) {
        return queues.stream().mapToLong(mapper).sum();
    }

    /**
     * 将 JSON 数值安全转换为长整数，字段缺失或格式异常时按零处理。
     */
    private static long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    /**
     * 将 JSON 字段安全转换为字符串，字段缺失时返回空值。
     */
    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * 构造脱敏的 DOWN 证据，不向模型暴露底层 HTTP 异常内容。
     */
    private RabbitMqStatusEvidence downEvidence(long startedAt) {
        return new RabbitMqStatusEvidence(
                EVIDENCE_SOURCE,
                targetProperties.name(),
                COMPONENT,
                "DOWN",
                false,
                elapsedMillis(startedAt),
                null,
                null,
                properties.vhost(),
                0,
                0,
                false,
                0,
                0,
                0,
                0,
                List.of(),
                ExceptionMessages.RABBITMQ_INSPECTION_FAILED
        );
    }

    /**
     * 计算从指定单调时钟起点到当前时刻的毫秒耗时。
     */
    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}

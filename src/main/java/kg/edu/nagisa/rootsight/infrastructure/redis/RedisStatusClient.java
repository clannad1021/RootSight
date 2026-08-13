package kg.edu.nagisa.rootsight.infrastructure.redis;

import kg.edu.nagisa.rootsight.common.constant.ExceptionMessages;
import kg.edu.nagisa.rootsight.config.InfrastructureTargetProperties;
import kg.edu.nagisa.rootsight.tool.evidence.RedisStatusEvidence;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * Redis 只读状态客户端，只执行 PING 和 INFO，不读取 Key，也不执行任何写命令。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisStatusClient {

    private static final String EVIDENCE_SOURCE = "REAL";
    private static final String COMPONENT = "redis";

    private final RedisConnectionFactory connectionFactory;
    private final InfrastructureTargetProperties targetProperties;

    /**
     * 读取当前配置 Redis 的连通性、版本、连接数、内存和命令统计。
     *
     * @return 成功或失败都以结构化证据返回，避免单个组件故障中断整个 Agent 诊断
     */
    public RedisStatusEvidence inspectStatus() {
        long startedAt = System.nanoTime();
        try (RedisConnection connection = connectionFactory.getConnection()) {
            String ping = connection.ping();
            Properties info = readInfoIfPermitted(connection);
            boolean metricsAvailable = info != null;

            return new RedisStatusEvidence(
                    EVIDENCE_SOURCE,
                    targetProperties.name(),
                    COMPONENT,
                    "UP",
                    true,
                    elapsedMillis(startedAt),
                    ping,
                    metricsAvailable,
                    property(info, "redis_version"),
                    property(info, "role"),
                    longProperty(info, "connected_clients"),
                    longProperty(info, "used_memory"),
                    longProperty(info, "total_commands_processed"),
                    longProperty(info, "keyspace_hits"),
                    longProperty(info, "keyspace_misses"),
                    metricsAvailable
                            ? "Redis PING 和 INFO 查询成功"
                            : ExceptionMessages.REDIS_INFO_UNAVAILABLE
            );
        } catch (RuntimeException exception) {
            // 日志只记录异常类型，避免连接串、用户名或供应商底层消息进入日志和模型上下文。
            log.warn("Redis status inspection failed: {}", exception.getClass().getSimpleName());
            return new RedisStatusEvidence(
                    EVIDENCE_SOURCE,
                    targetProperties.name(),
                    COMPONENT,
                    "DOWN",
                    false,
                    elapsedMillis(startedAt),
                    null,
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    ExceptionMessages.REDIS_INSPECTION_FAILED
            );
        }
    }

    /**
     * INFO 属于增强状态证据；监控账号没有该权限时仍保留已经成功的 PING 结果。
     */
    private Properties readInfoIfPermitted(RedisConnection connection) {
        try {
            return connection.serverCommands().info();
        } catch (RuntimeException exception) {
            log.info("Redis INFO metrics are unavailable for the configured monitoring account");
            return null;
        }
    }

    /**
     * 从可选 INFO 结果中安全读取字符串属性，指标不可用时返回空值。
     */
    private static String property(Properties properties, String name) {
        return properties == null ? null : properties.getProperty(name);
    }

    /**
     * 从可选 INFO 结果中安全解析长整数属性，缺失或格式异常时返回空值。
     */
    private static Long longProperty(Properties properties, String name) {
        String value = property(properties, name);
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * 计算从指定单调时钟起点到当前时刻的毫秒耗时。
     */
    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}

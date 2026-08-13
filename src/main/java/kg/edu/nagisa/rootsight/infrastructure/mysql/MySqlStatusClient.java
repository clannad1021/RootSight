package kg.edu.nagisa.rootsight.infrastructure.mysql;

import kg.edu.nagisa.rootsight.common.constant.ExceptionMessages;
import kg.edu.nagisa.rootsight.config.InfrastructureTargetProperties;
import kg.edu.nagisa.rootsight.tool.evidence.MySqlStatusEvidence;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * MySQL 状态客户端，只执行代码内固定的只读 SQL，不接收 LLM 生成的 SQL 文本。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MySqlStatusClient {

    private static final String EVIDENCE_SOURCE = "REAL";
    private static final String COMPONENT = "mysql";
    private static final String SERVER_INFO_SQL = """
            SELECT VERSION() AS server_version,
                   @@hostname AS host_name,
                   @@port AS server_port,
                   @@read_only AS read_only_mode
            """;
    private static final String GLOBAL_STATUS_SQL = """
            SHOW GLOBAL STATUS
            WHERE Variable_name IN ('Uptime', 'Threads_connected', 'Threads_running', 'Questions', 'Slow_queries')
            """;

    private final JdbcTemplate jdbcTemplate;
    private final InfrastructureTargetProperties targetProperties;

    /**
     * 查询当前配置 MySQL 的连通性、实例信息和少量全局负载指标。
     *
     * @return 成功或失败都以结构化证据返回，不向模型暴露 JDBC 底层异常信息
     */
    public MySqlStatusEvidence inspectStatus() {
        long startedAt = System.nanoTime(); //单调递增的 nanoTime() 计算耗时，不受系统时间调整影响
        try {
            Map<String, Object> serverInfo = lowerCaseKeys(jdbcTemplate.queryForMap(SERVER_INFO_SQL));
            Map<String, Long> globalStatus = jdbcTemplate.query(GLOBAL_STATUS_SQL, resultSet -> {
                Map<String, Long> values = new LinkedHashMap<>();
                while (resultSet.next()) {
                    values.put(resultSet.getString("Variable_name"), parseLong(resultSet.getString("Value")));
                }
                return values;
            });

            return new MySqlStatusEvidence(
                    EVIDENCE_SOURCE,
                    targetProperties.name(),
                    COMPONENT,
                    "UP",
                    true,
                    elapsedMillis(startedAt),
                    stringValue(serverInfo.get("server_version")),
                    stringValue(serverInfo.get("host_name")),
                    integerValue(serverInfo.get("server_port")),
                    booleanValue(serverInfo.get("read_only_mode")),
                    globalStatus.get("Uptime"),
                    globalStatus.get("Threads_connected"),
                    globalStatus.get("Threads_running"),
                    globalStatus.get("Questions"),
                    globalStatus.get("Slow_queries"),
                    "MySQL 固定只读状态查询成功"
            );
        } catch (RuntimeException exception) {
            // 工具结果只返回稳定业务消息，底层连接异常类型仅写入服务端日志用于排查。
            log.warn("MySQL status inspection failed: {}", exception.getClass().getSimpleName());
            return new MySqlStatusEvidence(
                    EVIDENCE_SOURCE,
                    targetProperties.name(),
                    COMPONENT,
                    "DOWN",
                    false,
                    elapsedMillis(startedAt),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    ExceptionMessages.MYSQL_INSPECTION_FAILED
            );
        }
    }

    /**
     * 将 JDBC 返回的列名统一为小写，避免不同驱动的列名大小写差异影响取值。
     */
    private static Map<String, Object> lowerCaseKeys(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key.toLowerCase(Locale.ROOT), value));
        return result;
    }

    /**
     * 将非空 JDBC 值安全转换为字符串，空值保持为空。
     */
    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * 仅将 JDBC 数值转换为整数，无法确认类型时返回空值。
     */
    private static Integer integerValue(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    /**
     * 将 MySQL 数值型开关转换为布尔值，无法确认类型时返回空值。
     */
    private static Boolean booleanValue(Object value) {
        return value instanceof Number number ? number.intValue() != 0 : null;
    }

    /**
     * 将状态指标文本安全解析为长整数，格式异常时以空值表示指标不可用。
     */
    private static Long parseLong(String value) {
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

package kg.edu.nagisa.rootsight.common.constant;

import lombok.experimental.UtilityClass;

/**
 * API 校验与诊断异常消息集中定义处。
 *
 * <p>集中管理可以避免相同错误在 Controller、Service 和测试中出现多份不一致的硬编码。</p>
 */
@UtilityClass
public class ExceptionMessages {

    public static final String QUESTION_REQUIRED = "问题不能为空";
    public static final String QUESTION_TOO_LONG = "问题不能超过 2000 个字符";
    public static final String INVALID_REQUEST = "请求参数不合法";
    public static final String VALIDATION_FAILED_TITLE = "请求校验失败";
    public static final String EMPTY_MODEL_RESPONSE = "模型返回了空回答";
    public static final String MODEL_DIAGNOSIS_UNAVAILABLE = "AI 模型暂时无法完成诊断";
    public static final String DIAGNOSIS_UNAVAILABLE_TITLE = "诊断服务暂时不可用";
    public static final String DIAGNOSIS_CONTEXT_MISSING = "诊断 Tool 缺少请求上下文";
    public static final String DIAGNOSIS_TRACE_NOT_FOUND = "诊断 Tool 对应的轨迹会话不存在";
    public static final String MYSQL_INSPECTION_FAILED = "MySQL 连接或状态查询失败";
    public static final String REDIS_INSPECTION_FAILED = "Redis 连接或状态查询失败";
    public static final String REDIS_INFO_UNAVAILABLE = "Redis PING 成功，但当前账号无权读取 INFO 指标";
    public static final String RABBITMQ_INSPECTION_FAILED = "RabbitMQ Management API 连接或状态查询失败";
    public static final String LOKI_QUERY_FAILED = "Loki 连接或日志查询失败";
    public static final String LOKI_QUERY_INVALID = "日志查询条件不合法";
    public static final String PROMETHEUS_QUERY_FAILED = "Prometheus 连接或指标查询失败";
    public static final String PROMETHEUS_QUERY_INVALID = "指标查询条件不合法";
}

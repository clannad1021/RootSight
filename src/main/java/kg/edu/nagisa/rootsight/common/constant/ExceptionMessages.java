package kg.edu.nagisa.rootsight.common.constant;

import lombok.experimental.UtilityClass;

/**
 * API 校验与诊断异常消息集中定义处。
 *
 * <p>集中管理可以避免相同错误在 Controller、Service 和测试中出现多份不一致的硬编码。</p>
 */
@UtilityClass
public class ExceptionMessages {

    public static final String EVALUATION_SCENARIOS_REQUIRED = "评测场景不能为空";
    public static final String EVALUATION_SCENARIO_ID_REQUIRED = "评测场景 ID 不能为空";
    public static final String EVALUATION_SCENARIO_NAME_REQUIRED = "评测场景名称不能为空";
    public static final String EVALUATION_QUESTION_REQUIRED = "评测问题不能为空";
    public static final String EVALUATION_REQUIRED_TOOLS_REQUIRED = "评测场景必须声明至少一个必需 Tool";
    public static final String EVALUATION_ROOT_CAUSE_KEYWORDS_REQUIRED = "评测场景必须声明根因关键词组";
    public static final String EVALUATION_REQUEST_INVALID_TITLE = "评测请求不合法";
    public static final String EVALUATION_SCENARIO_LIMIT_EXCEEDED = "评测场景数量超过允许上限";
    public static final String EVALUATION_SCENARIO_ID_DUPLICATED = "评测场景 ID 不能重复";
    public static final String EVALUATION_SCENARIO_INVALID = "评测场景定义不完整";
    public static final String EVALUATION_TOOL_DEFINITION_INVALID = "评测 Tool 定义为空或超过允许上限";
    public static final String EVALUATION_KEYWORD_DEFINITION_INVALID = "根因关键词组为空或超过允许上限";
    public static final String EVALUATION_DURATION_INVALID = "评测最大耗时必须大于 0";
    public static final String EVALUATION_TOOL_F1_INVALID = "评测 Tool F1 阈值必须在 0 到 1 之间";
    public static final String EVALUATION_REQUEST_TIMEOUT = "批量评测超过允许的总时限";
    public static final String EVALUATION_TIMEOUT_TITLE = "评测请求超时";

    public static final String QUESTION_REQUIRED = "问题不能为空";
    public static final String QUESTION_TOO_LONG = "问题不能超过 2000 个字符";
    public static final String INVALID_REQUEST = "请求参数不合法";
    public static final String VALIDATION_FAILED_TITLE = "请求校验失败";
    public static final String EMPTY_MODEL_RESPONSE = "模型返回了空回答";
    public static final String MODEL_DIAGNOSIS_UNAVAILABLE = "AI 模型暂时无法完成诊断";
    public static final String DIAGNOSIS_UNAVAILABLE_TITLE = "诊断服务暂时不可用";
    public static final String DIAGNOSIS_CONTEXT_MISSING = "诊断 Tool 缺少请求上下文";
    public static final String DIAGNOSIS_TRACE_NOT_FOUND = "诊断 Tool 对应的轨迹会话不存在";
    public static final String DIAGNOSIS_WORKFLOW_ALREADY_EXISTS = "诊断工作流已经存在";
    public static final String DIAGNOSIS_WORKFLOW_NOT_FOUND = "诊断工作流不存在";
    public static final String DIAGNOSIS_WORKFLOW_TERMINATED = "诊断工作流已经结束";
    public static final String DIAGNOSIS_WORKFLOW_TIMEOUT = "诊断超过允许的总时限，请缩小问题范围后重试";
    public static final String DIAGNOSIS_TOOL_LIMIT_REACHED = "诊断已达到 Tool 调用次数上限，请缩小问题范围后重试";
    public static final String MYSQL_INSPECTION_FAILED = "MySQL 连接或状态查询失败";
    public static final String REDIS_INSPECTION_FAILED = "Redis 连接或状态查询失败";
    public static final String REDIS_INFO_UNAVAILABLE = "Redis PING 成功，但当前账号无权读取 INFO 指标";
    public static final String RABBITMQ_INSPECTION_FAILED = "RabbitMQ Management API 连接或状态查询失败";
    public static final String LOKI_QUERY_FAILED = "Loki 连接或日志查询失败";
    public static final String LOKI_QUERY_INVALID = "日志查询条件不合法";
    public static final String PROMETHEUS_QUERY_FAILED = "Prometheus 连接或指标查询失败";
    public static final String PROMETHEUS_QUERY_INVALID = "指标查询条件不合法";
    public static final String KNOWLEDGE_SOURCE_UNAVAILABLE = "运行知识源目录不可用";
    public static final String KNOWLEDGE_SOURCE_EMPTY = "运行知识源中没有可索引文档";
    public static final String KNOWLEDGE_INDEX_FAILED = "运行知识索引同步失败";
    public static final String KNOWLEDGE_QUERY_FAILED = "运行知识库连接或检索失败";
    public static final String KNOWLEDGE_QUERY_INVALID = "运行知识检索条件不合法";
}

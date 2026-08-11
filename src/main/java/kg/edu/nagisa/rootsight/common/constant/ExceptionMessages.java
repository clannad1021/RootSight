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
}

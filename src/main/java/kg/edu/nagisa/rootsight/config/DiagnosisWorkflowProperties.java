package kg.edu.nagisa.rootsight.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 单次诊断工作流的总时限和 Tool 调用预算。
 */
@ConfigurationProperties(prefix = "rootsight.diagnosis.workflow")
public record DiagnosisWorkflowProperties(
        Duration timeout,
        int maxToolCalls
) {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(90);
    private static final int DEFAULT_MAX_TOOL_CALLS = 8;

    /**
     * 返回可安全使用的总诊断时限，空值、零值或负值回退到默认值。
     */
    public Duration effectiveTimeout() {
        return timeout == null || timeout.isZero() || timeout.isNegative()
                ? DEFAULT_TIMEOUT
                : timeout;
    }

    /**
     * 返回至少为 1 的 Tool 调用上限，避免错误配置关闭所有取证能力。
     */
    public int effectiveMaxToolCalls() {
        return maxToolCalls <= 0 ? DEFAULT_MAX_TOOL_CALLS : maxToolCalls;
    }
}

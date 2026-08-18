package kg.edu.nagisa.rootsight.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Evaluation 批量评测的资源边界和默认评分阈值。
 */
@ConfigurationProperties(prefix = "rootsight.evaluation")
public record EvaluationProperties(
        boolean enabled,
        int maxScenarios,
        int maxToolsPerScenario,
        int maxKeywordGroups,
        int maxKeywordsPerGroup,
        int maxAnswerLength,
        Duration defaultMaxDuration,
        double defaultMinToolF1
) {

    private static final int DEFAULT_MAX_SCENARIOS = 10;
    private static final int DEFAULT_MAX_TOOLS_PER_SCENARIO = 10;
    private static final int DEFAULT_MAX_KEYWORD_GROUPS = 10;
    private static final int DEFAULT_MAX_KEYWORDS_PER_GROUP = 10;
    private static final int DEFAULT_MAX_ANSWER_LENGTH = 20_000;
    private static final Duration DEFAULT_MAX_DURATION = Duration.ofSeconds(90);
    private static final double DEFAULT_MIN_TOOL_F1 = 0.75D;

    /**
     * 返回单次批量评测允许包含的最大场景数。
     */
    public int effectiveMaxScenarios() {
        return maxScenarios <= 0 ? DEFAULT_MAX_SCENARIOS : maxScenarios;
    }

    /**
     * 返回单个场景允许声明的最大 Tool 数量。
     */
    public int effectiveMaxToolsPerScenario() {
        return maxToolsPerScenario <= 0 ? DEFAULT_MAX_TOOLS_PER_SCENARIO : maxToolsPerScenario;
    }

    /**
     * 返回单个场景允许声明的最大根因关键词组数量。
     */
    public int effectiveMaxKeywordGroups() {
        return maxKeywordGroups <= 0 ? DEFAULT_MAX_KEYWORD_GROUPS : maxKeywordGroups;
    }

    /**
     * 返回每个根因关键词组允许包含的最大同义词数量。
     */
    public int effectiveMaxKeywordsPerGroup() {
        return maxKeywordsPerGroup <= 0 ? DEFAULT_MAX_KEYWORDS_PER_GROUP : maxKeywordsPerGroup;
    }

    /**
     * 返回评测报告中允许保留的最大诊断正文长度。
     */
    public int effectiveMaxAnswerLength() {
        return maxAnswerLength <= 0 ? DEFAULT_MAX_ANSWER_LENGTH : maxAnswerLength;
    }

    /**
     * 返回场景未单独配置耗时阈值时使用的默认值。
     */
    public Duration effectiveDefaultMaxDuration() {
        return defaultMaxDuration == null || defaultMaxDuration.isZero() || defaultMaxDuration.isNegative()
                ? DEFAULT_MAX_DURATION
                : defaultMaxDuration;
    }

    /**
     * 返回场景未单独配置 Tool F1 阈值时使用的默认值。
     */
    public double effectiveDefaultMinToolF1() {
        return defaultMinToolF1 <= 0D || defaultMinToolF1 > 1D
                ? DEFAULT_MIN_TOOL_F1
                : defaultMinToolF1;
    }
}

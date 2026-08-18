package kg.edu.nagisa.rootsight.evaluation.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import kg.edu.nagisa.rootsight.common.constant.ExceptionMessages;

import java.util.List;

/**
 * 一个可重复执行的故障诊断评测场景。
 *
 * <p>requiredTools 表示定位该故障必须取得的证据，allowedTools 表示与该问题相关但并非必需的
 * 补充证据。rootCauseKeywordGroups 中每组只需命中一个同义词，但所有组都必须命中。</p>
 */
public record EvaluationScenario(
        @NotBlank(message = ExceptionMessages.EVALUATION_SCENARIO_ID_REQUIRED)
        String id,
        @NotBlank(message = ExceptionMessages.EVALUATION_SCENARIO_NAME_REQUIRED)
        String name,
        @NotBlank(message = ExceptionMessages.EVALUATION_QUESTION_REQUIRED)
        String question,
        @NotEmpty(message = ExceptionMessages.EVALUATION_REQUIRED_TOOLS_REQUIRED)
        List<String> requiredTools,
        List<String> allowedTools,
        @NotEmpty(message = ExceptionMessages.EVALUATION_ROOT_CAUSE_KEYWORDS_REQUIRED)
        List<List<String>> rootCauseKeywordGroups,
        Long maxDurationMs,
        Double minToolF1
) {

    /**
     * 复制全部集合，保证同一场景在整个异步诊断期间保持不变。
     */
    public EvaluationScenario {
        requiredTools = requiredTools == null ? List.of() : List.copyOf(requiredTools);
        allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
        rootCauseKeywordGroups = rootCauseKeywordGroups == null
                ? List.of()
                : rootCauseKeywordGroups.stream()
                .map(group -> group == null ? List.<String>of() : List.copyOf(group))
                .toList();
    }
}

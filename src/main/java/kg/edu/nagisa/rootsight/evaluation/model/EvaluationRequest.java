package kg.edu.nagisa.rootsight.evaluation.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import kg.edu.nagisa.rootsight.common.constant.ExceptionMessages;

import java.util.List;

/**
 * 一次批量诊断评测请求。
 */
public record EvaluationRequest(
        @NotEmpty(message = ExceptionMessages.EVALUATION_SCENARIOS_REQUIRED)
        List<@Valid EvaluationScenario> scenarios
) {

    /**
     * 将场景列表复制为不可变集合，避免评测过程中被调用方修改。
     */
    public EvaluationRequest {
        scenarios = scenarios == null ? List.of() : List.copyOf(scenarios);
    }
}

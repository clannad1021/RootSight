package kg.edu.nagisa.rootsight.evaluation.model;

import kg.edu.nagisa.rootsight.agent.workflow.DiagnosisWorkflowState;

import java.util.List;

/**
 * 单个故障场景的诊断输出和各项评分明细。
 */
public record EvaluationScenarioResult(
        String scenarioId,
        String scenarioName,
        boolean passed,
        boolean rootCauseLocated,
        double toolPrecision,
        double toolRecall,
        double toolF1,
        boolean durationWithinLimit,
        long elapsedMs,
        long maxDurationMs,
        DiagnosisWorkflowState terminalState,
        List<String> selectedTools,
        List<String> missingRequiredTools,
        List<String> unexpectedTools,
        String answer,
        String terminalMessage
) {

    /**
     * 将报告中的 Tool 集合固定为不可变快照。
     */
    public EvaluationScenarioResult {
        selectedTools = List.copyOf(selectedTools);
        missingRequiredTools = List.copyOf(missingRequiredTools);
        unexpectedTools = List.copyOf(unexpectedTools);
        answer = answer == null ? "" : answer;
        terminalMessage = terminalMessage == null ? "" : terminalMessage;
    }
}

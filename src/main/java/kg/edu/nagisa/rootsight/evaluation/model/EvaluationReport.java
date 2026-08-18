package kg.edu.nagisa.rootsight.evaluation.model;

import java.time.Instant;
import java.util.List;

/**
 * 一次批量 Evaluation 的聚合指标和场景明细。
 */
public record EvaluationReport(
        Instant startedAt,
        Instant completedAt,
        int totalScenarios,
        int passedScenarios,
        double passRate,
        double rootCauseAccuracy,
        double toolSelectionAccuracy,
        long averageDurationMs,
        List<EvaluationScenarioResult> results
) {

    /**
     * 固定场景结果列表，保证返回后的报告不会被外部修改。
     */
    public EvaluationReport {
        results = List.copyOf(results);
    }
}

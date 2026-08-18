package kg.edu.nagisa.rootsight.evaluation;

import kg.edu.nagisa.rootsight.agent.DiagnosisService;
import kg.edu.nagisa.rootsight.agent.model.DiagnosisStreamEvent;
import kg.edu.nagisa.rootsight.agent.trace.ToolCallTrace;
import kg.edu.nagisa.rootsight.agent.workflow.DiagnosisWorkflowSnapshot;
import kg.edu.nagisa.rootsight.agent.workflow.DiagnosisWorkflowState;
import kg.edu.nagisa.rootsight.common.constant.ExceptionMessages;
import kg.edu.nagisa.rootsight.common.exception.EvaluationRequestException;
import kg.edu.nagisa.rootsight.config.EvaluationProperties;
import kg.edu.nagisa.rootsight.evaluation.model.EvaluationReport;
import kg.edu.nagisa.rootsight.evaluation.model.EvaluationRequest;
import kg.edu.nagisa.rootsight.evaluation.model.EvaluationScenario;
import kg.edu.nagisa.rootsight.evaluation.model.EvaluationScenarioResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 执行可重复的故障诊断场景，并计算根因、Tool 选择和耗时指标。
 */
@Service
@RequiredArgsConstructor
public class DiagnosisEvaluationService {

    private final DiagnosisService diagnosisService;
    private final EvaluationProperties properties;

    /**
     * 校验并顺序执行一批评测场景，最后生成包含总体指标和逐场景明细的报告。
     *
     * <p>场景使用 concatMap 顺序执行，避免批量评测同时占用过多模型配额和基础设施连接。</p>
     */
    public Mono<EvaluationReport> evaluate(EvaluationRequest request) {
        return Mono.defer(() -> {
            validateRequest(request);
            Instant startedAt = Instant.now();
            return Flux.fromIterable(request.scenarios())
                    .concatMap(this::evaluateScenario)
                    .collectList()
                    .map(results -> createReport(startedAt, results));
        });
    }

    /**
     * 调用现有流式诊断服务并收集一个场景的正文、终态和 Tool 轨迹。
     */
    private Mono<EvaluationScenarioResult> evaluateScenario(EvaluationScenario scenario) {
        return diagnosisService.diagnoseStream(scenario.question())
                .collectList()
                .map(events -> scoreScenario(scenario, events))
                .onErrorResume(exception -> Mono.just(scoreScenario(
                        scenario,
                        List.of(DiagnosisStreamEvent.error(
                                ExceptionMessages.MODEL_DIAGNOSIS_UNAVAILABLE,
                                List.of(),
                                new DiagnosisWorkflowSnapshot(DiagnosisWorkflowState.FAILED, 0, 0, 0)
                        ))
                )));
    }

    /**
     * 根据完整事件流计算根因命中、Tool Precision/Recall/F1 和耗时是否达标。
     */
    private EvaluationScenarioResult scoreScenario(
            EvaluationScenario scenario,
            List<DiagnosisStreamEvent> events
    ) {
        String answer = events.stream()
                .filter(event -> event.type() == DiagnosisStreamEvent.Type.CONTENT)
                .map(DiagnosisStreamEvent::content)
                .reduce("", String::concat);
        DiagnosisStreamEvent terminalEvent = findTerminalEvent(events);
        DiagnosisWorkflowSnapshot workflow = terminalEvent.workflow();
        DiagnosisWorkflowState terminalState = workflow == null
                ? DiagnosisWorkflowState.FAILED
                : workflow.state();
        long elapsedMs = workflow == null ? 0L : workflow.elapsedMs();

        List<String> selectedTools = terminalEvent.toolCalls().stream()
                .map(ToolCallTrace::toolName)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        Set<String> requiredTools = normalizeToolNames(scenario.requiredTools());
        Set<String> allowedTools = effectiveAllowedTools(scenario, requiredTools);
        List<String> missingRequiredTools = requiredTools.stream()
                .filter(tool -> !selectedTools.contains(tool))
                .toList();
        List<String> unexpectedTools = selectedTools.stream()
                .filter(tool -> !allowedTools.contains(tool))
                .toList();

        long correctSelections = selectedTools.stream().filter(allowedTools::contains).count();
        long requiredSelections = requiredTools.stream().filter(selectedTools::contains).count();
        double precision = selectedTools.isEmpty() ? 0D : (double) correctSelections / selectedTools.size();
        double recall = requiredTools.isEmpty() ? 1D : (double) requiredSelections / requiredTools.size();
        double toolF1 = precision + recall == 0D ? 0D : 2D * precision * recall / (precision + recall);

        boolean rootCauseLocated = matchesRootCause(answer, scenario.rootCauseKeywordGroups());
        long maxDurationMs = effectiveMaxDurationMs(scenario);
        boolean durationWithinLimit = elapsedMs >= 0L && elapsedMs <= maxDurationMs;
        boolean passed = terminalState == DiagnosisWorkflowState.COMPLETED
                && rootCauseLocated
                && toolF1 >= effectiveMinToolF1(scenario)
                && durationWithinLimit;
        String terminalMessage = terminalEvent.type() == DiagnosisStreamEvent.Type.ERROR
                ? terminalEvent.content()
                : "";

        return new EvaluationScenarioResult(
                scenario.id(),
                scenario.name(),
                passed,
                rootCauseLocated,
                roundScore(precision),
                roundScore(recall),
                roundScore(toolF1),
                durationWithinLimit,
                elapsedMs,
                maxDurationMs,
                terminalState,
                selectedTools,
                missingRequiredTools,
                unexpectedTools,
                truncateAnswer(answer),
                terminalMessage
        );
    }

    /**
     * 从事件流中取得最后一个完成或错误事件；缺少终态时生成安全的失败事件。
     */
    private DiagnosisStreamEvent findTerminalEvent(List<DiagnosisStreamEvent> events) {
        for (int index = events.size() - 1; index >= 0; index--) {
            DiagnosisStreamEvent event = events.get(index);
            if (event.type() == DiagnosisStreamEvent.Type.COMPLETED
                    || event.type() == DiagnosisStreamEvent.Type.ERROR) {
                return event;
            }
        }
        return DiagnosisStreamEvent.error(
                ExceptionMessages.MODEL_DIAGNOSIS_UNAVAILABLE,
                List.of(),
                new DiagnosisWorkflowSnapshot(DiagnosisWorkflowState.FAILED, 0, 0, 0)
        );
    }

    /**
     * 判断诊断正文是否对每个根因语义组至少命中一个允许的同义词。
     */
    private boolean matchesRootCause(String answer, List<List<String>> keywordGroups) {
        String normalizedAnswer = answer.toLowerCase(Locale.ROOT);
        return keywordGroups.stream().allMatch(group -> group.stream()
                .map(String::trim)
                .map(keyword -> keyword.toLowerCase(Locale.ROOT))
                .anyMatch(normalizedAnswer::contains));
    }

    /**
     * 汇总全部场景结果，计算通过率、根因准确率、平均 Tool F1 和平均耗时。
     */
    private EvaluationReport createReport(
            Instant startedAt,
            List<EvaluationScenarioResult> results
    ) {
        int total = results.size();
        long passed = results.stream().filter(EvaluationScenarioResult::passed).count();
        long rootCauseLocated = results.stream().filter(EvaluationScenarioResult::rootCauseLocated).count();
        double averageToolF1 = results.stream()
                .mapToDouble(EvaluationScenarioResult::toolF1)
                .average()
                .orElse(0D);
        long averageDurationMs = Math.round(results.stream()
                .mapToLong(EvaluationScenarioResult::elapsedMs)
                .average()
                .orElse(0D));

        return new EvaluationReport(
                startedAt,
                Instant.now(),
                total,
                Math.toIntExact(passed),
                roundScore(total == 0 ? 0D : (double) passed / total),
                roundScore(total == 0 ? 0D : (double) rootCauseLocated / total),
                roundScore(averageToolF1),
                averageDurationMs,
                results
        );
    }

    /**
     * 检查批量大小、场景唯一性和全部可配置集合的资源上限。
     */
    private void validateRequest(EvaluationRequest request) {
        if (request == null || request.scenarios().isEmpty()) {
            throw new EvaluationRequestException(ExceptionMessages.EVALUATION_SCENARIOS_REQUIRED);
        }
        if (request.scenarios().size() > properties.effectiveMaxScenarios()) {
            throw new EvaluationRequestException(ExceptionMessages.EVALUATION_SCENARIO_LIMIT_EXCEEDED);
        }

        Set<String> scenarioIds = new LinkedHashSet<>();
        for (EvaluationScenario scenario : request.scenarios()) {
            validateScenario(scenario);
            if (!scenarioIds.add(scenario.id().trim())) {
                throw new EvaluationRequestException(ExceptionMessages.EVALUATION_SCENARIO_ID_DUPLICATED);
            }
        }
    }

    /**
     * 检查单个场景的 Tool、关键词、阈值和持续时间定义是否可评分。
     */
    private void validateScenario(EvaluationScenario scenario) {
        if (scenario == null
                || !StringUtils.hasText(scenario.id())
                || !StringUtils.hasText(scenario.name())
                || !StringUtils.hasText(scenario.question())
                || scenario.requiredTools().isEmpty()
                || scenario.rootCauseKeywordGroups().isEmpty()) {
            throw new EvaluationRequestException(ExceptionMessages.EVALUATION_SCENARIO_INVALID);
        }

        Set<String> allTools = new LinkedHashSet<>(scenario.requiredTools());
        allTools.addAll(scenario.allowedTools());
        if (allTools.size() > properties.effectiveMaxToolsPerScenario()
                || allTools.stream().anyMatch(tool -> !StringUtils.hasText(tool))) {
            throw new EvaluationRequestException(ExceptionMessages.EVALUATION_TOOL_DEFINITION_INVALID);
        }
        if (scenario.rootCauseKeywordGroups().size() > properties.effectiveMaxKeywordGroups()
                || scenario.rootCauseKeywordGroups().stream().anyMatch(this::isInvalidKeywordGroup)) {
            throw new EvaluationRequestException(ExceptionMessages.EVALUATION_KEYWORD_DEFINITION_INVALID);
        }
        if (scenario.maxDurationMs() != null && scenario.maxDurationMs() <= 0L) {
            throw new EvaluationRequestException(ExceptionMessages.EVALUATION_DURATION_INVALID);
        }
        if (scenario.minToolF1() != null
                && (scenario.minToolF1() < 0D || scenario.minToolF1() > 1D)) {
            throw new EvaluationRequestException(ExceptionMessages.EVALUATION_TOOL_F1_INVALID);
        }
    }

    /**
     * 判断根因关键词组是否为空、超过上限或包含空关键词。
     */
    private boolean isInvalidKeywordGroup(List<String> group) {
        return group.isEmpty()
                || group.size() > properties.effectiveMaxKeywordsPerGroup()
                || group.stream().anyMatch(keyword -> !StringUtils.hasText(keyword));
    }

    /**
     * 去除 Tool 名称首尾空白并保持声明顺序和唯一性。
     */
    private Set<String> normalizeToolNames(List<String> toolNames) {
        Set<String> normalized = new LinkedHashSet<>();
        toolNames.stream().map(String::trim).forEach(normalized::add);
        return normalized;
    }

    /**
     * 合并允许 Tool 与必需 Tool，避免场景遗漏 allowedTools 时把必需证据误判为额外调用。
     */
    private Set<String> effectiveAllowedTools(
            EvaluationScenario scenario,
            Set<String> requiredTools
    ) {
        Set<String> allowed = new LinkedHashSet<>(requiredTools);
        scenario.allowedTools().stream().map(String::trim).forEach(allowed::add);
        return allowed;
    }

    /**
     * 返回场景自己的最大耗时，未声明时回退到 Evaluation 默认值。
     */
    private long effectiveMaxDurationMs(EvaluationScenario scenario) {
        return scenario.maxDurationMs() == null
                ? properties.effectiveDefaultMaxDuration().toMillis()
                : scenario.maxDurationMs();
    }

    /**
     * 返回场景自己的最低 Tool F1，未声明时回退到 Evaluation 默认值。
     */
    private double effectiveMinToolF1(EvaluationScenario scenario) {
        return scenario.minToolF1() == null
                ? properties.effectiveDefaultMinToolF1()
                : scenario.minToolF1();
    }

    /**
     * 限制报告中的正文长度，避免批量结果无限放大响应体。
     */
    private String truncateAnswer(String answer) {
        int maxLength = properties.effectiveMaxAnswerLength();
        return answer.length() <= maxLength ? answer : answer.substring(0, maxLength);
    }

    /**
     * 将比例统一保留四位小数，便于不同评测报告直接比较。
     */
    private double roundScore(double score) {
        return Math.round(score * 10_000D) / 10_000D;
    }
}

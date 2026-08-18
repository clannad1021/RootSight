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
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class DiagnosisEvaluationServiceTests {

    /**
     * 验证根因、必需 Tool 和耗时全部满足阈值时，场景和聚合报告都会通过。
     */
    @Test
    void shouldPassScenarioAndAggregateMetrics() {
        DiagnosisService diagnosisService = mock(DiagnosisService.class);
        given(diagnosisService.diagnoseStream("检查 Redis 故障"))
                .willReturn(Flux.just(
                        DiagnosisStreamEvent.content("诊断结论：Redis 连接失败，当前状态为 DOWN。"),
                        DiagnosisStreamEvent.completed(
                                List.of(new ToolCallTrace("inspect_redis_status", "Redis status=DOWN")),
                                new DiagnosisWorkflowSnapshot(DiagnosisWorkflowState.COMPLETED, 1, 8, 1200)
                        )
                ));
        DiagnosisEvaluationService service = service(diagnosisService);

        EvaluationReport report = service.evaluate(new EvaluationRequest(List.of(redisScenario())))
                .block(Duration.ofSeconds(2));

        assertThat(report).isNotNull();
        assertThat(report.totalScenarios()).isEqualTo(1);
        assertThat(report.passedScenarios()).isEqualTo(1);
        assertThat(report.passRate()).isEqualTo(1D);
        assertThat(report.rootCauseAccuracy()).isEqualTo(1D);
        assertThat(report.toolSelectionAccuracy()).isEqualTo(1D);
        assertThat(report.averageDurationMs()).isEqualTo(1200L);
        assertThat(report.results().get(0).passed()).isTrue();
    }

    /**
     * 验证无关 Tool 会降低 Precision 和 F1，并在明细中被标记为 unexpectedTools。
     */
    @Test
    void shouldFailWhenUnrelatedToolLowersF1BelowThreshold() {
        DiagnosisService diagnosisService = mock(DiagnosisService.class);
        given(diagnosisService.diagnoseStream("检查 Redis 故障"))
                .willReturn(Flux.just(
                        DiagnosisStreamEvent.content("Redis 无法连接，状态为 DOWN。"),
                        DiagnosisStreamEvent.completed(
                                List.of(
                                        new ToolCallTrace("inspect_redis_status", "Redis status=DOWN"),
                                        new ToolCallTrace("inspect_mysql_status", "MySQL status=UP")
                                ),
                                new DiagnosisWorkflowSnapshot(DiagnosisWorkflowState.COMPLETED, 2, 8, 1500)
                        )
                ));
        DiagnosisEvaluationService service = service(diagnosisService);

        EvaluationReport report = service.evaluate(new EvaluationRequest(List.of(redisScenario())))
                .block(Duration.ofSeconds(2));

        assertThat(report).isNotNull();
        EvaluationScenarioResult result = report.results().get(0);
        assertThat(result.passed()).isFalse();
        assertThat(result.toolPrecision()).isEqualTo(0.5D);
        assertThat(result.toolRecall()).isEqualTo(1D);
        assertThat(result.toolF1()).isEqualTo(0.6667D);
        assertThat(result.unexpectedTools()).containsExactly("inspect_mysql_status");
    }

    /**
     * 验证重复场景 ID 会在调用模型前被拒绝，防止报告明细无法唯一对应场景。
     */
    @Test
    void shouldRejectDuplicatedScenarioIds() {
        DiagnosisService diagnosisService = mock(DiagnosisService.class);
        DiagnosisEvaluationService service = service(diagnosisService);
        EvaluationScenario first = redisScenario();
        EvaluationScenario duplicated = new EvaluationScenario(
                first.id(), "重复场景", first.question(), first.requiredTools(), first.allowedTools(),
                first.rootCauseKeywordGroups(), first.maxDurationMs(), first.minToolF1()
        );

        assertThatThrownBy(() -> service.evaluate(new EvaluationRequest(List.of(first, duplicated))).block())
                .isInstanceOf(EvaluationRequestException.class)
                .hasMessage(ExceptionMessages.EVALUATION_SCENARIO_ID_DUPLICATED);
    }

    /**
     * 创建只要求 Redis 状态 Tool 且要求根因同时包含组件和不可用状态的测试场景。
     */
    private EvaluationScenario redisScenario() {
        return new EvaluationScenario(
                "redis-down",
                "Redis 不可用",
                "检查 Redis 故障",
                List.of("inspect_redis_status"),
                List.of(),
                List.of(List.of("Redis"), List.of("DOWN", "无法连接")),
                5000L,
                0.75D
        );
    }

    /**
     * 使用固定边界配置创建被测 Evaluation 服务，避免测试依赖外部配置文件。
     */
    private DiagnosisEvaluationService service(DiagnosisService diagnosisService) {
        EvaluationProperties properties = new EvaluationProperties(
                true,
                10,
                10,
                10,
                10,
                20_000,
                Duration.ofSeconds(90),
                0.75D
        );
        return new DiagnosisEvaluationService(diagnosisService, properties);
    }
}

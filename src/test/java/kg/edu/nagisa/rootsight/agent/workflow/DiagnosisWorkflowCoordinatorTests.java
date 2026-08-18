package kg.edu.nagisa.rootsight.agent.workflow;

import kg.edu.nagisa.rootsight.agent.trace.ToolCallTraceRecorder;
import kg.edu.nagisa.rootsight.common.constant.ExceptionMessages;
import kg.edu.nagisa.rootsight.config.DiagnosisWorkflowProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiagnosisWorkflowCoordinatorTests {

    /**
     * 验证工作流按规划、取证、归纳和完成的顺序发布状态快照。
     */
    @Test
    void shouldPublishControlledWorkflowStates() {
        DiagnosisWorkflowCoordinator coordinator = coordinator(Duration.ofSeconds(30), 4);
        String diagnosisId = "diagnosis-state";
        coordinator.start(diagnosisId);
        List<DiagnosisWorkflowSnapshot> snapshots = new CopyOnWriteArrayList<>();
        coordinator.observe(diagnosisId).subscribe(snapshots::add);

        coordinator.beforeToolCall(context(diagnosisId));
        coordinator.beginSynthesis(diagnosisId);
        coordinator.complete(diagnosisId);

        assertThat(snapshots)
                .extracting(DiagnosisWorkflowSnapshot::state)
                .containsExactly(
                        DiagnosisWorkflowState.PLANNING,
                        DiagnosisWorkflowState.EVIDENCE_COLLECTION,
                        DiagnosisWorkflowState.SYNTHESIS,
                        DiagnosisWorkflowState.COMPLETED
                );
        assertThat(coordinator.snapshot(diagnosisId).toolCallCount()).isEqualTo(1);
    }

    /**
     * 验证并行预占 Tool 额度时也不会突破配置上限。
     */
    @Test
    void shouldEnforceToolCallBudgetAtomically() {
        DiagnosisWorkflowCoordinator coordinator = coordinator(Duration.ofSeconds(30), 5);
        String diagnosisId = "diagnosis-budget";
        coordinator.start(diagnosisId);
        AtomicInteger acceptedCalls = new AtomicInteger();

        IntStream.range(0, 20).parallel().forEach(index -> {
            try {
                coordinator.beforeToolCall(context(diagnosisId));
                acceptedCalls.incrementAndGet();
            } catch (IllegalStateException ignored) {
                // 达到预算后的并行请求都应被工作流拒绝。
            }
        });

        DiagnosisWorkflowSnapshot snapshot = coordinator.snapshot(diagnosisId);
        assertThat(acceptedCalls).hasValue(5);
        assertThat(snapshot.toolCallCount()).isEqualTo(5);
        assertThat(snapshot.state()).isEqualTo(DiagnosisWorkflowState.TOOL_LIMIT_REACHED);
    }

    /**
     * 验证截止时间已过时会在外部 Tool 执行前返回统一超时错误。
     */
    @Test
    void shouldRejectToolCallAfterDeadline() throws InterruptedException {
        DiagnosisWorkflowCoordinator coordinator = coordinator(Duration.ofMillis(2), 4);
        String diagnosisId = "diagnosis-timeout";
        coordinator.start(diagnosisId);
        Thread.sleep(10);

        assertThatThrownBy(() -> coordinator.beforeToolCall(context(diagnosisId)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(ExceptionMessages.DIAGNOSIS_WORKFLOW_TIMEOUT);
        assertThat(coordinator.snapshot(diagnosisId).state())
                .isEqualTo(DiagnosisWorkflowState.TIMED_OUT);
    }

    /**
     * 创建使用指定时限和预算的真实协调器。
     */
    private DiagnosisWorkflowCoordinator coordinator(Duration timeout, int maxToolCalls) {
        return new DiagnosisWorkflowCoordinator(
                new DiagnosisWorkflowProperties(timeout, maxToolCalls)
        );
    }

    /**
     * 创建携带请求级诊断 ID 的 ToolContext。
     */
    private ToolContext context(String diagnosisId) {
        return new ToolContext(Map.of(
                ToolCallTraceRecorder.DIAGNOSIS_ID_CONTEXT_KEY,
                diagnosisId
        ));
    }
}

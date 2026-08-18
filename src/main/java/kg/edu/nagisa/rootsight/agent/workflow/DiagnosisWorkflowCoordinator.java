package kg.edu.nagisa.rootsight.agent.workflow;

import kg.edu.nagisa.rootsight.agent.trace.ToolCallTraceRecorder;
import kg.edu.nagisa.rootsight.common.constant.ExceptionMessages;
import kg.edu.nagisa.rootsight.config.DiagnosisWorkflowProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 管理请求级诊断状态、总截止时间和 Tool 预算，并向 SSE 流发布状态快照。
 */
@Component
@RequiredArgsConstructor
public class DiagnosisWorkflowCoordinator {

    private final DiagnosisWorkflowProperties properties;
    private final ConcurrentMap<String, WorkflowSession> sessions = new ConcurrentHashMap<>();

    /**
     * 为新的诊断 ID 创建独立工作流，并发布初始规划状态。
     */
    public void start(String diagnosisId) {
        Duration timeout = properties.effectiveTimeout();
        WorkflowSession session = new WorkflowSession(
                System.nanoTime(), timeout.toNanos(), properties.effectiveMaxToolCalls()
        );
        if (sessions.putIfAbsent(diagnosisId, session) != null) {
            throw new IllegalStateException(ExceptionMessages.DIAGNOSIS_WORKFLOW_ALREADY_EXISTS);
        }
        session.publishCurrentState();
    }

    /**
     * 返回某次诊断的状态事件流，供 REST SSE 和 JavaFX 展示工作流进度。
     */
    public Flux<DiagnosisWorkflowSnapshot> observe(String diagnosisId) {
        return requireSession(diagnosisId).statusSink.asFlux();
    }

    /**
     * 在真实 Tool 执行前原子预占一次调用额度，超时或超限时阻止外部查询发生。y
     */
    public void beforeToolCall(ToolContext toolContext) {
        requireSession(readDiagnosisId(toolContext)).reserveToolCall();
    }

    /**
     * 模型开始输出最终正文时进入归纳状态，之后不再期望继续调用 Tool。
     */
    public void beginSynthesis(String diagnosisId) {
        requireSession(diagnosisId).transitionTo(DiagnosisWorkflowState.SYNTHESIS, false);
    }

    /**
     * 将工作流标记为正常完成并关闭状态发布流。
     */
    public void complete(String diagnosisId) {
        requireSession(diagnosisId).transitionTo(DiagnosisWorkflowState.COMPLETED, true);
    }

    /**
     * 将尚未进入其他终态的工作流标记为失败。
     */
    public void fail(String diagnosisId) {
        requireSession(diagnosisId).transitionTo(DiagnosisWorkflowState.FAILED, true);
    }

    /**
     * 在总截止时间到达时标记超时，使错误事件能返回准确终态。
     */
    public void timeout(String diagnosisId) {
        requireSession(diagnosisId).transitionTo(DiagnosisWorkflowState.TIMED_OUT, true);
    }

    /**
     * 返回当前不可变状态快照，用于内容和终止事件关联同一次工作流状态。
     */
    public DiagnosisWorkflowSnapshot snapshot(String diagnosisId) {
        return requireSession(diagnosisId).snapshot();
    }

    /**
     * 在 SSE 完成、失败或客户端取消后移除请求级状态，避免长期占用内存。
     */
    public void clear(String diagnosisId) {
        WorkflowSession session = sessions.remove(diagnosisId);
        if (session != null) {
            session.completeStatusSink();
        }
    }

    /**
     * 根据诊断 ID 获取状态会话，不存在时返回集中定义的安全异常。y
     */
    private WorkflowSession requireSession(String diagnosisId) {
        WorkflowSession session = sessions.get(diagnosisId);
        if (session == null) {
            throw new IllegalStateException(ExceptionMessages.DIAGNOSIS_WORKFLOW_NOT_FOUND);
        }
        return session;
    }

    /**
     * 从 ToolContext 读取诊断 ID，保证并发 Tool 调用只修改所属工作流。
     */
    private String readDiagnosisId(ToolContext toolContext) {
        Map<String, Object> context = toolContext == null ? Map.of() : toolContext.getContext();
        Object diagnosisId = context.get(ToolCallTraceRecorder.DIAGNOSIS_ID_CONTEXT_KEY);
        if (!(diagnosisId instanceof String value) || value.isBlank()) {
            throw new IllegalStateException(ExceptionMessages.DIAGNOSIS_CONTEXT_MISSING);
        }
        return value;
    }

    /**
     * 保存单次诊断的可变计数和状态；所有修改都在同步方法内完成，确保并行 Tool 不突破预算。y
     */
    @RequiredArgsConstructor
    private static final class WorkflowSession {

        private final long startedAtNanos;
        private final long timeoutNanos;
        private final int maxToolCalls;
        private final Sinks.Many<DiagnosisWorkflowSnapshot> statusSink = Sinks.many().replay().latest();
        private DiagnosisWorkflowState state = DiagnosisWorkflowState.PLANNING;
        private int toolCallCount;

        /**
         * 在截止时间和次数预算内预占一次 Tool 调用，并发布最新取证进度。
         */
        private synchronized void reserveToolCall() {
            if (elapsedNanos() >= timeoutNanos) {
                transitionTo(DiagnosisWorkflowState.TIMED_OUT, true);
                throw new IllegalStateException(ExceptionMessages.DIAGNOSIS_WORKFLOW_TIMEOUT);
            }
            if (isTerminal(state)) {
                throw new IllegalStateException(ExceptionMessages.DIAGNOSIS_WORKFLOW_TERMINATED);
            }
            if (toolCallCount >= maxToolCalls) {
                transitionTo(DiagnosisWorkflowState.TOOL_LIMIT_REACHED, true);
                throw new IllegalStateException(ExceptionMessages.DIAGNOSIS_TOOL_LIMIT_REACHED);
            }
            toolCallCount++;
            state = DiagnosisWorkflowState.EVIDENCE_COLLECTION;
            publishCurrentState();
        }

        /**
         * 切换到指定状态；终态一旦确定，不再被普通失败状态覆盖。
         */
        private synchronized void transitionTo(DiagnosisWorkflowState nextState, boolean terminal) {
            if (isTerminal(state)) {
                return;
            }
            state = nextState;
            publishCurrentState();
            if (terminal) {
                completeStatusSink();
            }
        }

        /**
         * 发布当前快照；单会话同步保证 Reactor Sink 不发生并发写入冲突。
         */
        private synchronized void publishCurrentState() {
            statusSink.tryEmitNext(snapshot());
        }

        /**
         * 完成状态流；重复完成由 Reactor Sink 安全忽略。
         */
        private synchronized void completeStatusSink() {
            statusSink.tryEmitComplete();
        }

        /**
         * 生成不含内部 ID 和截止时间的客户端安全快照。
         */
        private synchronized DiagnosisWorkflowSnapshot snapshot() {
            return new DiagnosisWorkflowSnapshot(
                    state, toolCallCount, maxToolCalls, elapsedNanos() / 1_000_000
            );
        }

        /**
         * 使用单调时钟计算已耗时，避免系统时间调整影响超时判断。
         */
        private long elapsedNanos() {
            return Math.max(0, System.nanoTime() - startedAtNanos);
        }

        /**
         * 判断状态是否已经不可逆结束。
         */
        private static boolean isTerminal(DiagnosisWorkflowState candidate) {
            return candidate == DiagnosisWorkflowState.COMPLETED
                    || candidate == DiagnosisWorkflowState.FAILED
                    || candidate == DiagnosisWorkflowState.TIMED_OUT
                    || candidate == DiagnosisWorkflowState.TOOL_LIMIT_REACHED;
        }
    }
}

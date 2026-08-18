package kg.edu.nagisa.rootsight.agent.workflow;

/**
 * 单次诊断从理解问题到结束的有限状态集合。
 */
public enum DiagnosisWorkflowState {
    PLANNING,
    EVIDENCE_COLLECTION,
    SYNTHESIS,
    COMPLETED,
    FAILED,
    TIMED_OUT,
    TOOL_LIMIT_REACHED
}

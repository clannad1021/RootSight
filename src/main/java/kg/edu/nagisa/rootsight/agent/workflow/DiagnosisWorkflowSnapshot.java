package kg.edu.nagisa.rootsight.agent.workflow;

/**
 * 返回给客户端的工作流只读快照，不包含内部诊断 ID、提示词或敏感配置。
 */
public record DiagnosisWorkflowSnapshot(
        DiagnosisWorkflowState state,
        int toolCallCount,
        int maxToolCalls,
        long elapsedMs
) {
}

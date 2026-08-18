package kg.edu.nagisa.rootsight.api.dto;

import kg.edu.nagisa.rootsight.agent.model.DiagnosisStreamEvent;
import kg.edu.nagisa.rootsight.agent.trace.ToolCallTrace;
import kg.edu.nagisa.rootsight.agent.workflow.DiagnosisWorkflowSnapshot;

import java.util.List;

/**
 * SSE 诊断响应。STATUS 携带工作流进度，COMPLETED/ERROR 携带最终状态与 Tool 轨迹。
 */
public record DiagnosisResponse(
        DiagnosisStreamEvent.Type type,
        String content,
        List<ToolCallTrace> toolCalls,
        DiagnosisWorkflowSnapshot workflow
) {
}

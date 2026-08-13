package kg.edu.nagisa.rootsight.api.dto;

import kg.edu.nagisa.rootsight.agent.model.DiagnosisStreamEvent;
import kg.edu.nagisa.rootsight.agent.trace.ToolCallTrace;

import java.util.List;

/**
 * SSE 诊断响应。CONTENT 事件携带正文增量，COMPLETED/ERROR 事件携带最终 Tool 轨迹。
 */
public record DiagnosisResponse(DiagnosisStreamEvent.Type type, String content, List<ToolCallTrace> toolCalls) {
}

package kg.edu.nagisa.rootsight.api.dto;

import kg.edu.nagisa.rootsight.agent.trace.ToolCallTrace;

import java.util.List;

/**
 * 诊断响应。Tool 轨迹用于展示 Agent 实际取证路径，不等同于模型的文字推断。
 */
public record DiagnosisResponse(String answer, List<ToolCallTrace> toolCalls) {
}

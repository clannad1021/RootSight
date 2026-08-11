package kg.edu.nagisa.rootsight.agent.model;

import kg.edu.nagisa.rootsight.agent.trace.ToolCallTrace;

import java.util.List;

/**
 * Agent 层的诊断结果，包含模型结论和可审计的取证轨迹。
 */
public record DiagnosisResult(String answer, List<ToolCallTrace> toolCalls) {
}

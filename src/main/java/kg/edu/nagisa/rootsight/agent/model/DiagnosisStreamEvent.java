package kg.edu.nagisa.rootsight.agent.model;

import kg.edu.nagisa.rootsight.agent.trace.ToolCallTrace;
import kg.edu.nagisa.rootsight.agent.workflow.DiagnosisWorkflowSnapshot;

import java.util.List;

/**
 * Agent 流式诊断事件：内容事件用于增量展示，结束事件携带完整 Tool 轨迹。
 */
public record DiagnosisStreamEvent(
        Type type,
        String content,
        List<ToolCallTrace> toolCalls,
        DiagnosisWorkflowSnapshot workflow
) {

    public enum Type {
        STATUS,
        CONTENT,
        COMPLETED,
        ERROR
    }

    /**
     * 统一把空正文和空轨迹规范化为不可变的安全默认值，简化事件消费端判空逻辑。
     */
    public DiagnosisStreamEvent {
        content = content == null ? "" : content;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    /**
     * 创建携带模型增量正文的内容事件。
     */
    public static DiagnosisStreamEvent content(String content) {
        return new DiagnosisStreamEvent(Type.CONTENT, content, List.of(), null);
    }

    /**
     * 创建携带工作流阶段和 Tool 预算进度的状态事件。
     */
    public static DiagnosisStreamEvent status(DiagnosisWorkflowSnapshot workflow) {
        return new DiagnosisStreamEvent(Type.STATUS, "", List.of(), workflow);
    }

    /**
     * 创建表示诊断正常结束并携带完整 Tool 轨迹的完成事件。
     */
    public static DiagnosisStreamEvent completed(List<ToolCallTrace> toolCalls) {
        return completed(toolCalls, null);
    }

    /**
     * 创建同时携带 Tool 轨迹与最终工作流快照的完成事件。
     */
    public static DiagnosisStreamEvent completed(
            List<ToolCallTrace> toolCalls,
            DiagnosisWorkflowSnapshot workflow
    ) {
        return new DiagnosisStreamEvent(Type.COMPLETED, "", toolCalls, workflow);
    }

    /**
     * 创建携带安全错误消息和失败前 Tool 轨迹的错误事件。
     */
    public static DiagnosisStreamEvent error(String message, List<ToolCallTrace> toolCalls) {
        return error(message, toolCalls, null);
    }

    /**
     * 创建同时携带失败前 Tool 轨迹与终态快照的错误事件。
     */
    public static DiagnosisStreamEvent error(
            String message,
            List<ToolCallTrace> toolCalls,
            DiagnosisWorkflowSnapshot workflow
    ) {
        return new DiagnosisStreamEvent(Type.ERROR, message, toolCalls, workflow);
    }
}

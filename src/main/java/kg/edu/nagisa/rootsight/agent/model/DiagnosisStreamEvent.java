package kg.edu.nagisa.rootsight.agent.model;

import kg.edu.nagisa.rootsight.agent.trace.ToolCallTrace;

import java.util.List;

/**
 * Agent 流式诊断事件：内容事件用于增量展示，结束事件携带完整 Tool 轨迹。
 */
public record DiagnosisStreamEvent(Type type, String content, List<ToolCallTrace> toolCalls) {

    public enum Type {
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
        return new DiagnosisStreamEvent(Type.CONTENT, content, List.of());
    }

    /**
     * 创建表示诊断正常结束并携带完整 Tool 轨迹的完成事件。
     */
    public static DiagnosisStreamEvent completed(List<ToolCallTrace> toolCalls) {
        return new DiagnosisStreamEvent(Type.COMPLETED, "", toolCalls);
    }

    /**
     * 创建携带安全错误消息和失败前 Tool 轨迹的错误事件。
     */
    public static DiagnosisStreamEvent error(String message, List<ToolCallTrace> toolCalls) {
        return new DiagnosisStreamEvent(Type.ERROR, message, toolCalls);
    }
}

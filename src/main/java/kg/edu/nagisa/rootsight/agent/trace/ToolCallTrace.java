package kg.edu.nagisa.rootsight.agent.trace;

/**
 * 一次 Tool 执行的轻量记录。
 *
 * @param toolName Tool 注册给模型的方法名
 * @param summary  本次 Tool 返回证据的简要说明
 */
public record ToolCallTrace(String toolName, String summary) {
}

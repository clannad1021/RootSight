package kg.edu.nagisa.rootsight.desktop.constant;

import lombok.experimental.UtilityClass;

/**
 * 桌面客户端状态文本集中定义处，避免 Controller 中散落重复字符串。
 */
@UtilityClass
public class DesktopMessages {

    public static final String READY = "等待诊断任务";
    public static final String RUNNING = "Agent 正在流式诊断…";
    public static final String EMPTY_QUESTION = "请先描述需要诊断的故障现象";
    public static final String EMPTY_RESULT = "诊断完成后，Agent 的结论会显示在这里。";
    public static final String EMPTY_TRACE = "尚未调用 Tool";
    public static final String FINISHED = "诊断完成";
    public static final String FAILED = "诊断失败";
    public static final String SETTINGS_LOAD_FAILED = "设置窗口加载失败";
    public static final String UNKNOWN_FAILURE = "发生未知错误，请查看应用日志";
    public static final String MAIN_ART_MISSING = "Desktop main art is missing";

    /**
     * 生成统一的 FXML 资源缺失消息。
     */
    public static String fxmlMissing(String resourcePath) {
        return "FXML resource not found: " + resourcePath;
    }
}

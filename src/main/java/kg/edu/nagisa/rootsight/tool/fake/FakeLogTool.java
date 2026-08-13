package kg.edu.nagisa.rootsight.tool.fake;

import kg.edu.nagisa.rootsight.agent.trace.ToolCallTraceRecorder;
import kg.edu.nagisa.rootsight.tool.evidence.ApplicationLogEvidence;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Stage 1B 的日志模拟 Tool，用固定日志验证模型能否从现象继续追查异常线索。
 */
@Component
@RequiredArgsConstructor
public class FakeLogTool {

    private final ToolCallTraceRecorder traceRecorder;

    /**
     * 查询目标服务最近五分钟的模拟异常日志。
     */
    @Tool(name = "query_recent_error_logs",
            description = "查询目标服务最近五分钟的 ERROR 和 WARN 日志。仅在近期日志有助于回答当前问题时调用。")
    public ApplicationLogEvidence queryRecentErrorLogs(
            @ToolParam(description = "需要诊断的目标服务名称") String targetService,
            ToolContext toolContext) {
        ApplicationLogEvidence evidence = new ApplicationLogEvidence(
                "DEMO",
                targetService,
                "LAST_5_MINUTES",
                List.of(
                        "Redis connection timeout after 3000 ms",
                        "Cache read failed; falling back to primary data store"
                )
        );
        traceRecorder.record(toolContext, "query_recent_error_logs",
                "[DEMO] 目标=" + targetService + "，发现 Redis 超时与缓存降级日志");
        return evidence;
    }
}

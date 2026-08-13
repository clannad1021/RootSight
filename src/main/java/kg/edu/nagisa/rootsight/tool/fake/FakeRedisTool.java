package kg.edu.nagisa.rootsight.tool.fake;

import kg.edu.nagisa.rootsight.agent.trace.ToolCallTraceRecorder;
import kg.edu.nagisa.rootsight.tool.evidence.ComponentHealthEvidence;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * Stage 1B 的 Redis 模拟 Tool，用于对日志中出现的组件线索进行二次确认。
 */
@Component
@RequiredArgsConstructor
public class FakeRedisTool {

    private final ToolCallTraceRecorder traceRecorder;

    /**
     * 检查演示环境 Redis 的模拟健康状态。
     */
    @Tool(name = "check_redis_health",
            description = "检查演示目标 Redis 是否可连接以及当前健康状态。仅在该演示证据有助于回答当前问题时调用。")
    public ComponentHealthEvidence checkRedisHealth(ToolContext toolContext) {
        ComponentHealthEvidence evidence = new ComponentHealthEvidence(
                "DEMO",
                "redis",
                "DOWN",
                false,
                "Connection refused"
        );
        traceRecorder.record(toolContext, "check_redis_health", "[DEMO] Redis 状态=DOWN，连接被拒绝");
        return evidence;
    }
}

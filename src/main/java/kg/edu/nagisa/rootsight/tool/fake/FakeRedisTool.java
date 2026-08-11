package kg.edu.nagisa.rootsight.tool.fake;

import kg.edu.nagisa.rootsight.agent.trace.ToolCallTraceRecorder;
import kg.edu.nagisa.rootsight.tool.evidence.ComponentHealthEvidence;
import lombok.RequiredArgsConstructor;
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
            description = "检查 Redis 是否可连接以及当前健康状态。日志出现 Redis 超时或连接异常时调用。")
    public ComponentHealthEvidence checkRedisHealth() {
        ComponentHealthEvidence evidence = new ComponentHealthEvidence(
                "redis",
                "DOWN",
                false,
                "Connection refused"
        );
        traceRecorder.record("check_redis_health", "Redis 状态=DOWN，连接被拒绝");
        return evidence;
    }
}

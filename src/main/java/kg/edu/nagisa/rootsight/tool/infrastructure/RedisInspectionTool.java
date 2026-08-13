package kg.edu.nagisa.rootsight.tool.infrastructure;

import kg.edu.nagisa.rootsight.agent.trace.ToolCallTraceRecorder;
import kg.edu.nagisa.rootsight.infrastructure.redis.RedisStatusClient;
import kg.edu.nagisa.rootsight.tool.evidence.RedisStatusEvidence;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * 向 Agent 暴露 Redis 真实只读状态检查能力。
 */
@Component
@RequiredArgsConstructor
public class RedisInspectionTool {

    private final RedisStatusClient redisStatusClient;
    private final ToolCallTraceRecorder traceRecorder;

    /**
     * 检查当前配置目标的 Redis 服务状态，不读取 Key、不执行写命令。
     */
    @Tool(name = "inspect_redis_status",
            description = "读取当前目标 Redis 的真实 PING、版本、角色、连接数、内存和命令统计。仅在这些证据有助于回答当前问题时调用。")
    public RedisStatusEvidence inspectRedisStatus(ToolContext toolContext) {
        RedisStatusEvidence evidence = redisStatusClient.inspectStatus();
        traceRecorder.record(toolContext, "inspect_redis_status",
                "[REAL] 目标=" + evidence.targetName()
                        + "，Redis=" + evidence.status()
                        + "，INFO=" + (evidence.metricsAvailable() ? "AVAILABLE" : "UNAVAILABLE")
                        + "，耗时=" + evidence.responseTimeMs() + "ms");
        return evidence;
    }
}

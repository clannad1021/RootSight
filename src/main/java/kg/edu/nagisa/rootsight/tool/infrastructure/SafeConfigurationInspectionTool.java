package kg.edu.nagisa.rootsight.tool.infrastructure;

import kg.edu.nagisa.rootsight.agent.trace.ToolCallTraceRecorder;
import kg.edu.nagisa.rootsight.config.InfrastructureTargetProperties;
import kg.edu.nagisa.rootsight.config.RabbitMqManagementProperties;
import kg.edu.nagisa.rootsight.tool.evidence.SafeConfigurationEvidence;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 向 Agent 暴露固定白名单内的非敏感运行配置，不支持任意配置键查询。
 */
@Component
@RequiredArgsConstructor
public class SafeConfigurationInspectionTool {

    private static final List<String> AVAILABLE_READ_ONLY_TOOLS = List.of(
            "redis-status", "mysql-status", "rabbitmq-status"
    );
    private static final List<String> EXCLUDED_SENSITIVE_CATEGORIES = List.of(
            "passwords", "api-keys", "usernames", "connection-urls", "environment-variables"
    );

    private final Environment environment;
    private final InfrastructureTargetProperties targetProperties;
    private final RabbitMqManagementProperties rabbitMqProperties;
    private final ToolCallTraceRecorder traceRecorder;

    /**
     * 返回源码固定的安全配置摘要，帮助 Agent 理解当前诊断目标和能力边界。
     */
    @Tool(name = "inspect_safe_configuration",
            description = "读取当前 RootSight 实例的固定非敏感配置摘要，包括逻辑目标、应用名、模型名、服务端口、Redis 数据库编号、RabbitMQ vhost 和可用只读 Tool。不能查询密码、密钥、用户名、连接 URL 或任意环境变量。")
    public SafeConfigurationEvidence inspectSafeConfiguration(ToolContext toolContext) {
        SafeConfigurationEvidence evidence = new SafeConfigurationEvidence(
                "REAL",
                targetProperties.name(),
                environment.getProperty("spring.application.name", "root-sight"),
                environment.getProperty("spring.ai.deepseek.chat.model", "unknown"),
                environment.getProperty("server.port", Integer.class, 8081),
                environment.getProperty("spring.data.redis.database", Integer.class, 0),
                rabbitMqProperties.vhost(),
                AVAILABLE_READ_ONLY_TOOLS,
                EXCLUDED_SENSITIVE_CATEGORIES
        );
        traceRecorder.record(toolContext, "inspect_safe_configuration",
                "[REAL] 目标=" + evidence.targetName() + "，返回固定非敏感配置白名单");
        return evidence;
    }
}

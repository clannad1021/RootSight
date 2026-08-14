package kg.edu.nagisa.rootsight.tool.infrastructure;

import kg.edu.nagisa.rootsight.agent.trace.ToolCallTrace;
import kg.edu.nagisa.rootsight.agent.trace.ToolCallTraceRecorder;
import kg.edu.nagisa.rootsight.config.InfrastructureTargetProperties;
import kg.edu.nagisa.rootsight.config.RabbitMqManagementProperties;
import kg.edu.nagisa.rootsight.infrastructure.mysql.MySqlStatusClient;
import kg.edu.nagisa.rootsight.infrastructure.rabbitmq.RabbitMqStatusClient;
import kg.edu.nagisa.rootsight.infrastructure.redis.RedisStatusClient;
import kg.edu.nagisa.rootsight.tool.evidence.MySqlStatusEvidence;
import kg.edu.nagisa.rootsight.tool.evidence.RabbitMqStatusEvidence;
import kg.edu.nagisa.rootsight.tool.evidence.RedisStatusEvidence;
import kg.edu.nagisa.rootsight.tool.evidence.SafeConfigurationEvidence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.util.List;
import java.util.Map;
import java.time.Duration;

import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 验证真实基础设施 Tool 会原样返回客户端证据并留下可审计轨迹。
 */
class InfrastructureInspectionToolsTests {

    private ToolCallTraceRecorder traceRecorder;
    private String diagnosisId;
    private ToolContext toolContext;

    @BeforeEach
    void setUp() {
        traceRecorder = new ToolCallTraceRecorder();
        diagnosisId = traceRecorder.start();
        toolContext = new ToolContext(Map.of(
                ToolCallTraceRecorder.DIAGNOSIS_ID_CONTEXT_KEY,
                diagnosisId
        ));
    }

    @Test
    void shouldRecordRedisInspectionTrace() {
        RedisStatusClient client = mock(RedisStatusClient.class);
        RedisStatusEvidence expected = new RedisStatusEvidence(
                "REAL", "test-target", "redis", "UP", true, 5,
                "PONG", true, "7.4.0", "master", 3L, 1024L, 100L, 80L, 20L,
                "Redis PING 和 INFO 查询成功"
        );
        given(client.inspectStatus()).willReturn(expected);

        RedisStatusEvidence actual = new RedisInspectionTool(client, traceRecorder).inspectRedisStatus(toolContext);

        assertThat(actual).isEqualTo(expected);
        assertThat(toolNames()).containsExactly("inspect_redis_status");
        assertThat(traceRecorder.snapshot(diagnosisId).get(0).summary()).startsWith("[REAL]");
    }

    @Test
    void shouldRecordMySqlInspectionTrace() {
        MySqlStatusClient client = mock(MySqlStatusClient.class);
        MySqlStatusEvidence expected = new MySqlStatusEvidence(
                "REAL", "test-target", "mysql", "UP", true, 8,
                "8.4.0", "db-host", 3306, false,
                3600L, 4L, 1L, 200L, 0L,
                "MySQL 固定只读状态查询成功"
        );
        given(client.inspectStatus()).willReturn(expected);

        MySqlStatusEvidence actual = new MySqlInspectionTool(client, traceRecorder).inspectMySqlStatus(toolContext);

        assertThat(actual).isEqualTo(expected);
        assertThat(toolNames()).containsExactly("inspect_mysql_status");
        assertThat(traceRecorder.snapshot(diagnosisId).get(0).summary()).startsWith("[REAL]");
    }

    /**
     * 验证 RabbitMQ Tool 返回真实证据并把调用记录到当前诊断上下文。
     */
    @Test
    void shouldRecordRabbitMqInspectionTrace() {
        RabbitMqStatusClient client = mock(RabbitMqStatusClient.class);
        RabbitMqStatusEvidence expected = new RabbitMqStatusEvidence(
                "REAL", "test-target", "rabbitmq", "UP", true, 6,
                "4.2.9", "rabbit@test", "/", 0, 0, false,
                0, 0, 0, 0, List.of(),
                "RabbitMQ Management API 和指定 vhost 队列状态查询成功"
        );
        given(client.inspectStatus()).willReturn(expected);

        RabbitMqStatusEvidence actual =
                new RabbitMqInspectionTool(client, traceRecorder).inspectRabbitMqStatus(toolContext);

        assertThat(actual).isEqualTo(expected);
        assertThat(toolNames()).containsExactly("inspect_rabbitmq_status");
        assertThat(traceRecorder.snapshot(diagnosisId).get(0).summary()).startsWith("[REAL]");
    }

    /**
     * 验证安全配置 Tool 只返回固定白名单，并明确列出被排除的敏感配置类别。
     */
    @Test
    void shouldReturnOnlyWhitelistedConfiguration() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.application.name", "root-sight-test")
                .withProperty("spring.ai.deepseek.chat.model", "test-model")
                .withProperty("server.port", "9081")
                .withProperty("spring.data.redis.database", "3")
                .withProperty("ROOTSIGHT_RABBITMQ_PASSWORD", "must-not-leak");
        InfrastructureTargetProperties target = new InfrastructureTargetProperties("test-target");
        RabbitMqManagementProperties rabbit = new RabbitMqManagementProperties(
                "http://rabbit.test", "monitor", "must-not-leak", "/test",
                100, 20, Duration.ofSeconds(3), Duration.ofSeconds(5)
        );
        SafeConfigurationInspectionTool tool = new SafeConfigurationInspectionTool(
                environment, target, rabbit, traceRecorder
        );

        SafeConfigurationEvidence evidence = tool.inspectSafeConfiguration(toolContext);

        assertThat(evidence.evidenceSource()).isEqualTo("REAL");
        assertThat(evidence.applicationName()).isEqualTo("root-sight-test");
        assertThat(evidence.rabbitMqVhost()).isEqualTo("/test");
        assertThat(evidence.availableReadOnlyTools()).contains("rabbitmq-status");
        assertThat(evidence.excludedSensitiveCategories()).contains("passwords", "environment-variables");
        assertThat(evidence.toString()).doesNotContain("must-not-leak", "rabbit.test", "monitor");
        assertThat(toolNames()).containsExactly("inspect_safe_configuration");
    }

    private List<String> toolNames() {
        return traceRecorder.snapshot(diagnosisId).stream()
                .map(ToolCallTrace::toolName)
                .toList();
    }
}

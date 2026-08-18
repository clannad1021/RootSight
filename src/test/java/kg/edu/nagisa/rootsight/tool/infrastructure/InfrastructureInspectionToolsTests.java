package kg.edu.nagisa.rootsight.tool.infrastructure;

import kg.edu.nagisa.rootsight.agent.trace.ToolCallTrace;
import kg.edu.nagisa.rootsight.agent.trace.ToolCallTraceRecorder;
import kg.edu.nagisa.rootsight.agent.workflow.DiagnosisWorkflowCoordinator;
import kg.edu.nagisa.rootsight.config.DiagnosisWorkflowProperties;
import kg.edu.nagisa.rootsight.config.InfrastructureTargetProperties;
import kg.edu.nagisa.rootsight.config.LokiProperties;
import kg.edu.nagisa.rootsight.config.KnowledgeProperties;
import kg.edu.nagisa.rootsight.config.PrometheusProperties;
import kg.edu.nagisa.rootsight.config.RabbitMqManagementProperties;
import kg.edu.nagisa.rootsight.common.constant.ExceptionMessages;
import kg.edu.nagisa.rootsight.infrastructure.loki.LokiLogClient;
import kg.edu.nagisa.rootsight.infrastructure.mysql.MySqlStatusClient;
import kg.edu.nagisa.rootsight.infrastructure.prometheus.PrometheusMetricsClient;
import kg.edu.nagisa.rootsight.infrastructure.rabbitmq.RabbitMqStatusClient;
import kg.edu.nagisa.rootsight.infrastructure.redis.RedisStatusClient;
import kg.edu.nagisa.rootsight.knowledge.KnowledgeRetrievalService;
import kg.edu.nagisa.rootsight.tool.evidence.MySqlStatusEvidence;
import kg.edu.nagisa.rootsight.tool.evidence.LokiLogEvidence;
import kg.edu.nagisa.rootsight.tool.evidence.KnowledgeSearchEvidence;
import kg.edu.nagisa.rootsight.tool.evidence.KnowledgeSnippetEvidence;
import kg.edu.nagisa.rootsight.tool.evidence.RabbitMqStatusEvidence;
import kg.edu.nagisa.rootsight.tool.evidence.RedisStatusEvidence;
import kg.edu.nagisa.rootsight.tool.evidence.PrometheusMetricsEvidence;
import kg.edu.nagisa.rootsight.tool.evidence.SafeConfigurationEvidence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.util.List;
import java.util.Map;
import java.time.Duration;
import java.nio.file.Path;

import org.springframework.mock.env.MockEnvironment;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 验证真实基础设施 Tool 会原样返回客户端证据并留下可审计轨迹。
 */
class InfrastructureInspectionToolsTests {

    private ToolCallTraceRecorder traceRecorder;
    private DiagnosisWorkflowCoordinator workflowCoordinator;
    private String diagnosisId;
    private ToolContext toolContext;

    @BeforeEach
    void setUp() {
        traceRecorder = new ToolCallTraceRecorder();
        diagnosisId = traceRecorder.start();
        workflowCoordinator = new DiagnosisWorkflowCoordinator(
                new DiagnosisWorkflowProperties(Duration.ofSeconds(30), 20)
        );
        workflowCoordinator.start(diagnosisId);
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

        RedisStatusEvidence actual = new RedisInspectionTool(client, traceRecorder, workflowCoordinator)
                .inspectRedisStatus(toolContext);

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

        MySqlStatusEvidence actual = new MySqlInspectionTool(client, traceRecorder, workflowCoordinator)
                .inspectMySqlStatus(toolContext);

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
                new RabbitMqInspectionTool(client, traceRecorder, workflowCoordinator)
                        .inspectRabbitMqStatus(toolContext);

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
                .withProperty("spring.ai.openai.embedding.model", "BAAI/bge-m3")
                .withProperty("server.port", "9081")
                .withProperty("spring.data.redis.database", "3")
                .withProperty("ROOTSIGHT_RABBITMQ_PASSWORD", "must-not-leak");
        InfrastructureTargetProperties target = new InfrastructureTargetProperties("test-target");
        RabbitMqManagementProperties rabbit = new RabbitMqManagementProperties(
                "http://rabbit.test", "monitor", "must-not-leak", "/test",
                100, 20, Duration.ofSeconds(3), Duration.ofSeconds(5)
        );
        LokiProperties loki = lokiProperties();
        SafeConfigurationInspectionTool tool = new SafeConfigurationInspectionTool(
                environment, target, rabbit, loki, prometheusProperties(),
                knowledgeProperties(), traceRecorder, workflowCoordinator
        );

        SafeConfigurationEvidence evidence = tool.inspectSafeConfiguration(toolContext);

        assertThat(evidence.evidenceSource()).isEqualTo("REAL");
        assertThat(evidence.applicationName()).isEqualTo("root-sight-test");
        assertThat(evidence.rabbitMqVhost()).isEqualTo("/test");
        assertThat(evidence.lokiDefaultService()).isEqualTo("observed-target");
        assertThat(evidence.prometheusDefaultService()).isEqualTo("observed-target");
        assertThat(evidence.knowledgeSystem()).isEqualTo("observed-system");
        assertThat(evidence.embeddingModel()).isEqualTo("BAAI/bge-m3");
        assertThat(evidence.vectorStore()).isEqualTo("qdrant");
        assertThat(evidence.availableReadOnlyTools())
                .contains("rabbitmq-status", "loki-logs", "prometheus-metrics", "operational-knowledge");
        assertThat(evidence.excludedSensitiveCategories()).contains("passwords", "environment-variables");
        assertThat(evidence.toString()).doesNotContain("must-not-leak", "rabbit.test", "monitor");
        assertThat(toolNames()).containsExactly("inspect_safe_configuration");
    }

    /**
     * 验证 Loki Tool 返回真实日志证据，并把查询策略和命中数量写入当前诊断轨迹。
     */
    @Test
    void shouldRecordLokiLogInspectionTrace() {
        LokiLogClient client = mock(LokiLogClient.class);
        LokiLogEvidence expected = new LokiLogEvidence(
                "REAL", "test-target", "observed-target", "AVAILABLE", true, 12,
                null, null, "EXACT_RANGE", 1, 2, false, null,
                List.of(), true, List.of(), "Loki 日志查询成功"
        );
        given(client.queryLogs(null, null, "timeout", null, 20)).willReturn(expected);

        LokiLogEvidence actual = new LokiLogInspectionTool(client, traceRecorder, workflowCoordinator)
                .queryApplicationLogs(null, null, "timeout", null, 20, toolContext);

        assertThat(actual).isEqualTo(expected);
        assertThat(toolNames()).containsExactly("query_application_logs");
        assertThat(traceRecorder.snapshot(diagnosisId).get(0).summary())
                .contains("[REAL]", "策略=EXACT_RANGE", "命中=2");
    }

    /**
     * 验证 Prometheus Tool 返回真实指标证据，并记录查询状态和窗口。
     */
    @Test
    void shouldRecordPrometheusMetricsInspectionTrace() {
        PrometheusMetricsClient client = mock(PrometheusMetricsClient.class);
        PrometheusMetricsEvidence expected = new PrometheusMetricsEvidence(
                "REAL", "test-target", "observed-target", "UP", true, 9,
                java.time.Instant.parse("2026-08-16T01:00:00Z"), "5m", true,
                12.5, 2.0, 98.0, 150.0, 240.0, 4.5,
                1000.0, 2000.0, 18.0, "Prometheus 固定只读指标查询成功"
        );
        given(client.queryMetrics(null, null, "5m")).willReturn(expected);

        PrometheusMetricsEvidence actual = new PrometheusMetricsInspectionTool(
                client, traceRecorder, workflowCoordinator
        )
                .queryServiceHttpMetrics(null, null, "5m", toolContext);

        assertThat(actual).isEqualTo(expected);
        assertThat(toolNames()).containsExactly("query_service_http_metrics");
        assertThat(traceRecorder.snapshot(diagnosisId).get(0).summary())
                .contains("[REAL]", "Prometheus=UP", "窗口=5m");
    }

    /**
     * 验证知识 Tool 返回文档证据，并在轨迹中明确标记为非实时知识。
     */
    @Test
    void shouldRecordOperationalKnowledgeTrace() {
        KnowledgeRetrievalService service = mock(KnowledgeRetrievalService.class);
        KnowledgeSearchEvidence expected = new KnowledgeSearchEvidence(
                "REAL", "OPERATIONAL_KNOWLEDGE", false,
                "test-target", "observed-system", "AVAILABLE", true, 11, 1,
                List.of(new KnowledgeSnippetEvidence(
                        "docs/architecture.md", 2, 0.88, "Redis 故障时回源数据库"
                )),
                "知识库语义检索成功；结果仅表示系统文档和运行手册知识"
        );
        given(service.search("Redis 为什么故障开放", 3)).willReturn(expected);

        KnowledgeSearchEvidence actual = new KnowledgeInspectionTool(
                service, traceRecorder, workflowCoordinator
        )
                .searchOperationalKnowledge("Redis 为什么故障开放", 3, toolContext);

        assertThat(actual).isEqualTo(expected);
        assertThat(toolNames()).containsExactly("search_operational_knowledge");
        assertThat(traceRecorder.snapshot(diagnosisId).get(0).summary())
                .contains("[REAL-KNOWLEDGE]", "命中=1");
    }

    /**
     * 验证预算用尽后会在基础设施客户端执行前拒绝 Tool 调用。
     */
    @Test
    void shouldRejectToolBeforeExternalClientWhenBudgetIsExhausted() {
        DiagnosisWorkflowCoordinator limitedCoordinator = new DiagnosisWorkflowCoordinator(
                new DiagnosisWorkflowProperties(Duration.ofSeconds(30), 1)
        );
        limitedCoordinator.start(diagnosisId);
        limitedCoordinator.beforeToolCall(toolContext);
        MySqlStatusClient client = mock(MySqlStatusClient.class);
        MySqlInspectionTool tool = new MySqlInspectionTool(
                client, traceRecorder, limitedCoordinator
        );

        assertThatThrownBy(() -> tool.inspectMySqlStatus(toolContext))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(ExceptionMessages.DIAGNOSIS_TOOL_LIMIT_REACHED);
        verifyNoInteractions(client);
    }

    /**
     * 创建 Tool 测试使用的 Loki 查询策略配置。
     */
    private static LokiProperties lokiProperties() {
        return new LokiProperties(
                "http://loki.test", "service_name", "observed-target",
                50, 100, 120, 1000,
                Duration.ofMinutes(30),
                List.of(Duration.ofHours(2), Duration.ofHours(6), Duration.ofHours(24)),
                Duration.ofDays(7), Duration.ofMinutes(10), Duration.ofMinutes(20),
                Duration.ofSeconds(30), 20, Duration.ofSeconds(3), Duration.ofSeconds(8)
        );
    }

    /**
     * 创建 Tool 测试使用的 Prometheus 安全查询配置。
     */
    private static PrometheusProperties prometheusProperties() {
        return new PrometheusProperties(
                "http://prometheus.test", "application", "observed-target", "5m",
                List.of("1m", "5m", "15m", "30m", "1h"), 120,
                Duration.ofSeconds(5), Duration.ofSeconds(3), Duration.ofSeconds(8)
        );
    }

    /**
     * 创建 Tool 测试使用的知识来源和检索边界配置。
     */
    private static KnowledgeProperties knowledgeProperties() {
        return new KnowledgeProperties(
                true, false, Path.of("knowledge-base"), "observed-system",
                List.of("README.md", "docs/*.md"), List.of("docs/interview*.md"),
                100, DataSize.ofMegabytes(1), 800, 200, 20, 200,
                5, 10, 0.45, 500, 1200
        );
    }

    private List<String> toolNames() {
        return traceRecorder.snapshot(diagnosisId).stream()
                .map(ToolCallTrace::toolName)
                .toList();
    }
}

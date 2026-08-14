package kg.edu.nagisa.rootsight.infrastructure;

import kg.edu.nagisa.rootsight.common.constant.ExceptionMessages;
import kg.edu.nagisa.rootsight.config.InfrastructureTargetProperties;
import kg.edu.nagisa.rootsight.config.RabbitMqManagementProperties;
import kg.edu.nagisa.rootsight.infrastructure.mysql.MySqlStatusClient;
import kg.edu.nagisa.rootsight.infrastructure.rabbitmq.RabbitMqStatusClient;
import kg.edu.nagisa.rootsight.infrastructure.redis.RedisStatusClient;
import kg.edu.nagisa.rootsight.tool.evidence.MySqlStatusEvidence;
import kg.edu.nagisa.rootsight.tool.evidence.RabbitMqStatusEvidence;
import kg.edu.nagisa.rootsight.tool.evidence.RedisStatusEvidence;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.sql.SQLException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 验证基础设施故障会被转换成可供 Agent 分析的 DOWN 证据，而不是向外抛出底层异常。
 */
class InfrastructureStatusClientsTests {

    private static final InfrastructureTargetProperties TARGET =
            new InfrastructureTargetProperties("test-target");

    @Test
    void shouldReturnDownEvidenceWhenRedisConnectionFails() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        given(connectionFactory.getConnection())
                .willThrow(new RedisConnectionFailureException("secret redis endpoint"));
        RedisStatusClient client = new RedisStatusClient(connectionFactory, TARGET);

        RedisStatusEvidence evidence = client.inspectStatus();

        assertThat(evidence.evidenceSource()).isEqualTo("REAL");
        assertThat(evidence.targetName()).isEqualTo("test-target");
        assertThat(evidence.status()).isEqualTo("DOWN");
        assertThat(evidence.reachable()).isFalse();
        assertThat(evidence.detail()).isEqualTo(ExceptionMessages.REDIS_INSPECTION_FAILED);
        assertThat(evidence.detail()).doesNotContain("secret redis endpoint");
    }

    @Test
    void shouldReturnDownEvidenceWhenMySqlConnectionFails() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        given(jdbcTemplate.queryForMap(org.mockito.ArgumentMatchers.anyString()))
                .willThrow(new CannotGetJdbcConnectionException("secret jdbc url", new SQLException()));
        MySqlStatusClient client = new MySqlStatusClient(jdbcTemplate, TARGET);

        MySqlStatusEvidence evidence = client.inspectStatus();

        assertThat(evidence.evidenceSource()).isEqualTo("REAL");
        assertThat(evidence.targetName()).isEqualTo("test-target");
        assertThat(evidence.status()).isEqualTo("DOWN");
        assertThat(evidence.reachable()).isFalse();
        assertThat(evidence.detail()).isEqualTo(ExceptionMessages.MYSQL_INSPECTION_FAILED);
        assertThat(evidence.detail()).doesNotContain("secret jdbc url");
    }

    /**
     * 验证 RabbitMQ 客户端只查询编码后的目标 vhost，并正确聚合有界队列状态。
     */
    @Test
    void shouldReturnRabbitMqEvidenceForConfiguredVhost() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://rabbit.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://rabbit.test/api/overview"))
                .andRespond(withSuccess("""
                        {"rabbitmq_version":"4.2.9","cluster_name":"rabbit@test"}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://rabbit.test/api/queues/%2F?page=1&page_size=100"))
                .andRespond(withSuccess("""
                        {
                          "total_count": 1,
                          "items": [
                            {
                              "name": "jobs",
                              "state": "running",
                              "messages": 7,
                              "messages_ready": 5,
                              "messages_unacknowledged": 2,
                              "consumers": 1
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));
        RabbitMqStatusClient client = new RabbitMqStatusClient(
                builder.build(), rabbitProperties(), TARGET
        );

        RabbitMqStatusEvidence evidence = client.inspectStatus();

        server.verify();
        assertThat(evidence.evidenceSource()).isEqualTo("REAL");
        assertThat(evidence.status()).isEqualTo("UP");
        assertThat(evidence.reachable()).isTrue();
        assertThat(evidence.vhost()).isEqualTo("/");
        assertThat(evidence.totalQueueCount()).isEqualTo(1);
        assertThat(evidence.sampledMessagesReady()).isEqualTo(5);
        assertThat(evidence.sampledMessagesUnacknowledged()).isEqualTo(2);
        assertThat(evidence.queues()).singleElement().extracting("name").isEqualTo("jobs");
    }

    /**
     * 验证 Management API 故障会被转换为脱敏 DOWN 证据，而不会抛出底层 HTTP 异常。
     */
    @Test
    void shouldReturnDownEvidenceWhenRabbitMqManagementApiFails() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://secret-rabbit.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://secret-rabbit.test/api/overview"))
                .andRespond(withServerError());
        RabbitMqStatusClient client = new RabbitMqStatusClient(
                builder.build(), rabbitProperties(), TARGET
        );

        RabbitMqStatusEvidence evidence = client.inspectStatus();

        server.verify();
        assertThat(evidence.status()).isEqualTo("DOWN");
        assertThat(evidence.reachable()).isFalse();
        assertThat(evidence.detail()).isEqualTo(ExceptionMessages.RABBITMQ_INSPECTION_FAILED);
        assertThat(evidence.detail()).doesNotContain("secret-rabbit");
    }

    /**
     * 创建测试使用的 RabbitMQ Management API 非敏感配置。
     */
    private static RabbitMqManagementProperties rabbitProperties() {
        return new RabbitMqManagementProperties(
                "http://rabbit.test", "monitor", "secret", "/",
                100, 20, Duration.ofSeconds(3), Duration.ofSeconds(5)
        );
    }
}

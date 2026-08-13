package kg.edu.nagisa.rootsight.infrastructure;

import kg.edu.nagisa.rootsight.common.constant.ExceptionMessages;
import kg.edu.nagisa.rootsight.config.InfrastructureTargetProperties;
import kg.edu.nagisa.rootsight.infrastructure.mysql.MySqlStatusClient;
import kg.edu.nagisa.rootsight.infrastructure.redis.RedisStatusClient;
import kg.edu.nagisa.rootsight.tool.evidence.MySqlStatusEvidence;
import kg.edu.nagisa.rootsight.tool.evidence.RedisStatusEvidence;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

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
}

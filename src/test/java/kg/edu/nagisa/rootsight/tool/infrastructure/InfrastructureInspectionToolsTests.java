package kg.edu.nagisa.rootsight.tool.infrastructure;

import kg.edu.nagisa.rootsight.agent.trace.ToolCallTrace;
import kg.edu.nagisa.rootsight.agent.trace.ToolCallTraceRecorder;
import kg.edu.nagisa.rootsight.infrastructure.mysql.MySqlStatusClient;
import kg.edu.nagisa.rootsight.infrastructure.redis.RedisStatusClient;
import kg.edu.nagisa.rootsight.tool.evidence.MySqlStatusEvidence;
import kg.edu.nagisa.rootsight.tool.evidence.RedisStatusEvidence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.util.List;
import java.util.Map;

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

    private List<String> toolNames() {
        return traceRecorder.snapshot(diagnosisId).stream()
                .map(ToolCallTrace::toolName)
                .toList();
    }
}

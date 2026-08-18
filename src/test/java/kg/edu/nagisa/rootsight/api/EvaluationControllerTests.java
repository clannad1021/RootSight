package kg.edu.nagisa.rootsight.api;

import kg.edu.nagisa.rootsight.common.constant.ExceptionMessages;
import kg.edu.nagisa.rootsight.config.DiagnosisWorkflowProperties;
import kg.edu.nagisa.rootsight.evaluation.DiagnosisEvaluationService;
import kg.edu.nagisa.rootsight.evaluation.model.EvaluationReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EvaluationController.class, properties = "rootsight.evaluation.enabled=true")
class EvaluationControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DiagnosisEvaluationService evaluationService;

    @MockitoBean
    private DiagnosisWorkflowProperties workflowProperties;

    /**
     * 为每个控制器测试提供固定的单场景诊断总时限。
     */
    @BeforeEach
    void setUpWorkflowTimeout() {
        given(workflowProperties.effectiveTimeout()).willReturn(Duration.ofSeconds(90));
    }

    /**
     * 验证显式启用 Evaluation 后，API 会返回服务生成的聚合报告。
     */
    @Test
    void shouldReturnEvaluationReport() throws Exception {
        Instant now = Instant.parse("2026-08-18T12:00:00Z");
        given(evaluationService.evaluate(any())).willReturn(Mono.just(new EvaluationReport(
                now, now, 1, 1, 1D, 1D, 1D, 1200L, List.of()
        )));

        MvcResult mvcResult = mockMvc.perform(post("/api/evaluations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalScenarios").value(1))
                .andExpect(jsonPath("$.passRate").value(1D))
                .andExpect(jsonPath("$.averageDurationMs").value(1200));
    }

    /**
     * 验证空场景列表会由 Bean Validation 拒绝，且不会进入真实评测服务。
     */
    @Test
    void shouldRejectEmptyScenarios() throws Exception {
        mockMvc.perform(post("/api/evaluations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenarios\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value(ExceptionMessages.VALIDATION_FAILED_TITLE))
                .andExpect(jsonPath("$.detail").value(ExceptionMessages.EVALUATION_SCENARIOS_REQUIRED));

        verifyNoInteractions(evaluationService);
    }

    /**
     * 返回包含最小合法场景定义的 JSON 请求正文。
     */
    private String validRequest() {
        return """
                {
                  "scenarios": [{
                    "id": "redis-down",
                    "name": "Redis down",
                    "question": "检查 Redis",
                    "requiredTools": ["inspect_redis_status"],
                    "allowedTools": [],
                    "rootCauseKeywordGroups": [["Redis"], ["DOWN"]]
                  }]
                }
                """;
    }
}

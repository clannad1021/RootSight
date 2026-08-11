package kg.edu.nagisa.rootsight.api;

import kg.edu.nagisa.rootsight.agent.DiagnosisService;
import kg.edu.nagisa.rootsight.agent.model.DiagnosisResult;
import kg.edu.nagisa.rootsight.agent.trace.ToolCallTrace;
import kg.edu.nagisa.rootsight.common.constant.ExceptionMessages;
import kg.edu.nagisa.rootsight.common.exception.DiagnosisUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

@WebMvcTest(DiagnosisController.class)
class DiagnosisControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DiagnosisService diagnosisService;

    @Test
    void shouldReturnModelAnswer() throws Exception {
        given(diagnosisService.diagnose("订单服务为什么变慢了？"))
                .willReturn(new DiagnosisResult(
                        "演示证据显示 Redis 不可用导致请求回源，延迟因此升高。",
                        List.of(new ToolCallTrace("check_redis_health", "Redis 状态=DOWN"))
                ));

        mockMvc.perform(post("/api/diagnoses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"订单服务为什么变慢了？"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.answer")
                        .value("演示证据显示 Redis 不可用导致请求回源，延迟因此升高。"))
                .andExpect(jsonPath("$.toolCalls[0].toolName").value("check_redis_health"));
    }

    @Test
    void shouldRejectBlankQuestionBeforeCallingModel() throws Exception {
        mockMvc.perform(post("/api/diagnoses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value(ExceptionMessages.VALIDATION_FAILED_TITLE))
                .andExpect(jsonPath("$.detail").value(ExceptionMessages.QUESTION_REQUIRED));

        verifyNoInteractions(diagnosisService);
    }

    @Test
    void shouldReturnBadGatewayWithoutLeakingProviderFailure() throws Exception {
        given(diagnosisService.diagnose(anyString()))
                .willThrow(new DiagnosisUnavailableException(ExceptionMessages.MODEL_DIAGNOSIS_UNAVAILABLE));

        mockMvc.perform(post("/api/diagnoses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"检查目标服务"}
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value(ExceptionMessages.DIAGNOSIS_UNAVAILABLE_TITLE))
                .andExpect(jsonPath("$.detail").value(ExceptionMessages.MODEL_DIAGNOSIS_UNAVAILABLE));
    }
}

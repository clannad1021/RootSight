package kg.edu.nagisa.rootsight.api;

import kg.edu.nagisa.rootsight.agent.DiagnosisService;
import kg.edu.nagisa.rootsight.agent.model.DiagnosisStreamEvent;
import kg.edu.nagisa.rootsight.agent.trace.ToolCallTrace;
import kg.edu.nagisa.rootsight.agent.workflow.DiagnosisWorkflowSnapshot;
import kg.edu.nagisa.rootsight.agent.workflow.DiagnosisWorkflowState;
import kg.edu.nagisa.rootsight.common.constant.ExceptionMessages;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
@WebMvcTest(DiagnosisController.class)
class DiagnosisControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DiagnosisService diagnosisService;

    @Test
    void shouldStreamModelAnswerAndToolTrace() throws Exception {
        given(diagnosisService.diagnoseStream("订单服务为什么变慢了？"))
                .willReturn(Flux.just(
                        DiagnosisStreamEvent.status(new DiagnosisWorkflowSnapshot(
                                DiagnosisWorkflowState.PLANNING, 0, 8, 1
                        )),
                        DiagnosisStreamEvent.content("诊断结论：\n"),
                        DiagnosisStreamEvent.content("Redis 不可用导致延迟升高。"),
                        DiagnosisStreamEvent.completed(List.of(
                                new ToolCallTrace("check_redis_health", "Redis 状态=DOWN")
                        ))
                ));

        MvcResult mvcResult = mockMvc.perform(post("/api/diagnoses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"订单服务为什么变慢了？"}
                                """))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult streamedResult = mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString("event:status")))
                .andExpect(content().string(containsString("event:content")))
                .andExpect(content().string(containsString("event:completed")))
                .andExpect(content().string(containsString("\"state\":\"PLANNING\"")))
                .andExpect(content().string(containsString("check_redis_health")))
                .andReturn();

        // SSE 规范固定使用 UTF-8；MockMvc 的无参 getContentAsString 默认按 ISO-8859-1，测试需显式按协议解码。
        assertThat(streamedResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("诊断结论", "Redis 不可用导致延迟升高");
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
    void shouldStreamSafeErrorWithoutLeakingProviderFailure() throws Exception {
        given(diagnosisService.diagnoseStream("检查目标服务"))
                .willReturn(Flux.just(DiagnosisStreamEvent.error(
                        ExceptionMessages.MODEL_DIAGNOSIS_UNAVAILABLE,
                        List.of()
                )));

        MvcResult mvcResult = mockMvc.perform(post("/api/diagnoses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"检查目标服务"}
                                """))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult streamedResult = mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:error")))
                .andReturn();

        assertThat(streamedResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains(ExceptionMessages.MODEL_DIAGNOSIS_UNAVAILABLE);
    }
}

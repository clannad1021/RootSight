package kg.edu.nagisa.rootsight.api;

import kg.edu.nagisa.rootsight.agent.DiagnosisService;
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

@WebMvcTest(DiagnosisController.class)
class DiagnosisControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DiagnosisService diagnosisService;

    @Test
    void shouldReturnModelAnswer() throws Exception {
        given(diagnosisService.diagnose("ShortPan 为什么变慢了？"))
                .willReturn("当前阶段尚未接入真实指标，需要先采集延迟和日志证据。");

        mockMvc.perform(post("/api/diagnoses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"ShortPan 为什么变慢了？"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.answer")
                        .value("当前阶段尚未接入真实指标，需要先采集延迟和日志证据。"));
    }

    @Test
    void shouldRejectBlankQuestionBeforeCallingModel() throws Exception {
        mockMvc.perform(post("/api/diagnoses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("请求校验失败"))
                .andExpect(jsonPath("$.detail").value("问题不能为空"));

        verifyNoInteractions(diagnosisService);
    }

    @Test
    void shouldReturnBadGatewayWithoutLeakingProviderFailure() throws Exception {
        given(diagnosisService.diagnose(anyString()))
                .willThrow(new DiagnosisUnavailableException("DeepSeek 暂时无法完成诊断"));

        mockMvc.perform(post("/api/diagnoses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"检查 ShortPan"}
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("诊断服务暂时不可用"))
                .andExpect(jsonPath("$.detail").value("DeepSeek 暂时无法完成诊断"));
    }
}

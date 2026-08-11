package kg.edu.nagisa.rootsight.api;

import jakarta.validation.Valid;
import kg.edu.nagisa.rootsight.agent.model.DiagnosisResult;
import kg.edu.nagisa.rootsight.agent.DiagnosisService;
import kg.edu.nagisa.rootsight.api.dto.DiagnosisRequest;
import kg.edu.nagisa.rootsight.api.dto.DiagnosisResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对外提供通用故障诊断入口。
 */
@RestController
@RequestMapping("/api/diagnoses")
@RequiredArgsConstructor
public class DiagnosisController {

    private final DiagnosisService diagnosisService;

    /**
     * 接收用户故障描述并返回模型诊断与 Tool 调用轨迹。
     */
    @PostMapping
    public DiagnosisResponse diagnose(@Valid @RequestBody DiagnosisRequest request) {
        DiagnosisResult result = diagnosisService.diagnose(request.question());
        return new DiagnosisResponse(result.answer(), result.toolCalls());
    }
}

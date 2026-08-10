package kg.edu.nagisa.rootsight.api;

import jakarta.validation.Valid;
import kg.edu.nagisa.rootsight.agent.DiagnosisService;
import kg.edu.nagisa.rootsight.api.dto.DiagnosisRequest;
import kg.edu.nagisa.rootsight.api.dto.DiagnosisResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/diagnoses")
public class DiagnosisController {

    private final DiagnosisService diagnosisService;

    public DiagnosisController(DiagnosisService diagnosisService) {
        this.diagnosisService = diagnosisService;
    }

    @PostMapping
    public DiagnosisResponse diagnose(@Valid @RequestBody DiagnosisRequest request) {
        return new DiagnosisResponse(diagnosisService.diagnose(request.question()));
    }
}

package kg.edu.nagisa.rootsight.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kg.edu.nagisa.rootsight.common.constant.ExceptionMessages;

/**
 * 通用诊断请求，问题中可以包含任意目标系统或服务名称。
 */
public record DiagnosisRequest(
        @NotBlank(message = ExceptionMessages.QUESTION_REQUIRED)
        @Size(max = 2000, message = ExceptionMessages.QUESTION_TOO_LONG)
        String question
) {
}

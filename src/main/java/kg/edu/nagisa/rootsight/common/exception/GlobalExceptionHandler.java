package kg.edu.nagisa.rootsight.common.exception;

import kg.edu.nagisa.rootsight.common.constant.ExceptionMessages;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 把 Bean Validation 的字段错误转换成稳定的 Problem Detail 响应。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationException(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null
                        ? ExceptionMessages.INVALID_REQUEST
                        : error.getDefaultMessage())
                .orElse(ExceptionMessages.INVALID_REQUEST);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle(ExceptionMessages.VALIDATION_FAILED_TITLE);
        return problem;
    }

    /**
     * 隐藏模型供应商的底层异常，只向调用方暴露可控的业务消息。
     */
    @ExceptionHandler(DiagnosisUnavailableException.class)
    public ProblemDetail handleDiagnosisUnavailableException(DiagnosisUnavailableException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, exception.getMessage());
        problem.setTitle(ExceptionMessages.DIAGNOSIS_UNAVAILABLE_TITLE);
        return problem;
    }

    /**
     * 将评测场景之间的业务约束错误转换为稳定的 400 Problem Detail 响应。
     */
    @ExceptionHandler(EvaluationRequestException.class)
    public ProblemDetail handleEvaluationRequestException(EvaluationRequestException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle(ExceptionMessages.EVALUATION_REQUEST_INVALID_TITLE);
        return problem;
    }
}

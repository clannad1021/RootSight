package kg.edu.nagisa.rootsight.api;

import jakarta.validation.Valid;
import kg.edu.nagisa.rootsight.evaluation.DiagnosisEvaluationService;
import kg.edu.nagisa.rootsight.common.constant.ExceptionMessages;
import kg.edu.nagisa.rootsight.config.DiagnosisWorkflowProperties;
import kg.edu.nagisa.rootsight.evaluation.model.EvaluationReport;
import kg.edu.nagisa.rootsight.evaluation.model.EvaluationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import reactor.core.Disposable;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 提供需要显式启用的批量故障诊断评测入口。
 */
@RestController
@RequestMapping("/api/evaluations")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "rootsight.evaluation", name = "enabled", havingValue = "true")
public class EvaluationController {

    private static final long REQUEST_TIMEOUT_BUFFER_MS = 30_000L;

    private final DiagnosisEvaluationService evaluationService;
    private final DiagnosisWorkflowProperties workflowProperties;

    /**
     * 顺序执行调用方提交的评测场景，并使用独立于普通 MVC 请求的批量超时返回完整报告。
     */
    @PostMapping
    public DeferredResult<EvaluationReport> evaluate(@Valid @RequestBody EvaluationRequest request) {
        DeferredResult<EvaluationReport> deferredResult =
                new DeferredResult<>(calculateRequestTimeoutMs(request));
        AtomicReference<Disposable> subscriptionReference = new AtomicReference<>();

        deferredResult.onTimeout(() -> deferredResult.setErrorResult(createTimeoutProblem()));
        deferredResult.onCompletion(() -> disposeSubscription(subscriptionReference));

        Disposable subscription = evaluationService.evaluate(request).subscribe(
                deferredResult::setResult,
                deferredResult::setErrorResult
        );
        subscriptionReference.set(subscription);
        if (deferredResult.isSetOrExpired()) {
            subscription.dispose();
        }
        return deferredResult;
    }

    /**
     * 按“单次诊断总时限 × 场景数”计算批量请求时限，并预留报告汇总和网络传输缓冲。
     */
    private long calculateRequestTimeoutMs(EvaluationRequest request) {
        try {
            long diagnosisBudget = Math.multiplyExact(
                    workflowProperties.effectiveTimeout().toMillis(),
                    request.scenarios().size()
            );
            return Math.addExact(diagnosisBudget, REQUEST_TIMEOUT_BUFFER_MS);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    /**
     * 创建不暴露模型或基础设施细节的 Evaluation 网关超时响应。
     */
    private ProblemDetail createTimeoutProblem() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.GATEWAY_TIMEOUT,
                ExceptionMessages.EVALUATION_REQUEST_TIMEOUT
        );
        problem.setTitle(ExceptionMessages.EVALUATION_TIMEOUT_TITLE);
        return problem;
    }

    /**
     * 请求完成、超时或客户端断开后取消残留订阅，避免后台继续消耗模型配额。
     */
    private void disposeSubscription(AtomicReference<Disposable> subscriptionReference) {
        Disposable subscription = subscriptionReference.get();
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
    }
}

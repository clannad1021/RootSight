package kg.edu.nagisa.rootsight.agent;

import kg.edu.nagisa.rootsight.agent.format.DiagnosisAnswerFormatter;
import kg.edu.nagisa.rootsight.agent.model.DiagnosisStreamEvent;
import kg.edu.nagisa.rootsight.agent.trace.ToolCallTraceRecorder;
import kg.edu.nagisa.rootsight.common.constant.ExceptionMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 诊断应用服务，负责启动流式 Agent 调用并收集该次调用产生的 Tool 轨迹。
 */
@Service
@RequiredArgsConstructor
public class DiagnosisService {

    private final ChatClient diagnosisChatClient;
    private final ToolCallTraceRecorder traceRecorder;
    private final DiagnosisAnswerFormatter answerFormatter;

    /**
     * 根据用户描述执行流式诊断，并按模型生成进度持续返回纯文本内容。
     *
     * <p>每个订阅都会创建独立诊断 ID，并通过 ToolContext 传给实际执行的 Tool。
     * 最后一个 COMPLETED 事件再携带本次完整轨迹，保证正文流不会因等待全部回答而阻塞。</p>
     *
     * @param question 用户描述的故障现象
     * @return 包含增量正文、完成状态或安全错误消息的事件流
     */
    public Flux<DiagnosisStreamEvent> diagnoseStream(String question) {
        return Flux.defer(() -> createDiagnosisStream(question));
    }

    /**
     * 为单个订阅创建请求级上下文、正文流和终止事件，确保重复订阅不会共享状态。
     */
    private Flux<DiagnosisStreamEvent> createDiagnosisStream(String question) {
        String diagnosisId = traceRecorder.start();
        //线程安全的
        AtomicBoolean hasAnswerContent = new AtomicBoolean(false);
        DiagnosisAnswerFormatter.StreamFormatter formatter = answerFormatter.createStreamFormatter();

        Flux<DiagnosisStreamEvent> contentEvents = diagnosisChatClient.prompt()
                .user(question)
                .toolContext(Map.of(ToolCallTraceRecorder.DIAGNOSIS_ID_CONTEXT_KEY, diagnosisId))
                .stream()
                .content()
                .map(formatter::format)
                .filter(chunk -> !chunk.isEmpty())
                // 模型通常按 token 返回；以很小的时间窗合并可显著减少 SSE 与 JavaFX 刷新次数，同时保持实时感。
                .bufferTimeout(32, Duration.ofMillis(40))
                .map(chunks -> String.join("", chunks))
                .map(chunk -> {
                    if (StringUtils.hasText(chunk)) {
                        hasAnswerContent.set(true);
                    }
                    return DiagnosisStreamEvent.content(chunk);
                });

        Mono<DiagnosisStreamEvent> terminalEvent = Mono.defer(() -> {
            if (!hasAnswerContent.get()) {
                return Mono.just(DiagnosisStreamEvent.error(
                        ExceptionMessages.EMPTY_MODEL_RESPONSE,
                        traceRecorder.snapshot(diagnosisId)
                ));
            }
            return Mono.just(DiagnosisStreamEvent.completed(traceRecorder.snapshot(diagnosisId)));
        });

        return contentEvents
                .concatWith(terminalEvent)
                // SSE 响应可能已经开始写出，失败时必须通过流内 ERROR 事件返回安全消息，不能泄露供应商异常。
                .onErrorResume(exception -> Flux.just(DiagnosisStreamEvent.error(
                        ExceptionMessages.MODEL_DIAGNOSIS_UNAVAILABLE,
                        traceRecorder.snapshot(diagnosisId)
                )))
                // 客户端主动断开同样会触发 finally，确保不会遗留会话轨迹。
                .doFinally(signalType -> traceRecorder.clear(diagnosisId));
    }
}

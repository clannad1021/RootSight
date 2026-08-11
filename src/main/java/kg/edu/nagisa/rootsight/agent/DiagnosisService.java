package kg.edu.nagisa.rootsight.agent;

import kg.edu.nagisa.rootsight.agent.model.DiagnosisResult;
import kg.edu.nagisa.rootsight.agent.trace.ToolCallTraceRecorder;
import kg.edu.nagisa.rootsight.common.constant.ExceptionMessages;
import kg.edu.nagisa.rootsight.common.exception.DiagnosisUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 诊断应用服务，负责启动一次 Agent 调用并收集该次调用产生的 Tool 轨迹。
 */
@Service
@RequiredArgsConstructor
public class DiagnosisService {

    private final ChatClient diagnosisChatClient;
    private final ToolCallTraceRecorder traceRecorder;

    /**
     * 根据用户描述执行一次完整诊断。
     *
     * <p>当前阶段由 ChatClient 自动处理“模型选择 Tool → Java 执行 Tool → 结果返回模型”的循环，
     * 同时记录工具轨迹，便于调用方观察模型实际取过哪些证据。</p>
     *
     * @param question 用户描述的故障现象
     * @return 模型回答以及本次调用产生的 Tool 执行轨迹
     */
    public DiagnosisResult diagnose(String question) {
        traceRecorder.start();
        try {
            // 创建用户消息并发起同步调用；ToolCallingAdvisor 会在内部继续执行模型请求，直到模型给出最终回答。
            String answer = diagnosisChatClient.prompt()
                    .user(question)
                    .call()
                    .content();

            if (!StringUtils.hasText(answer)) {
                throw new DiagnosisUnavailableException(ExceptionMessages.EMPTY_MODEL_RESPONSE);
            }
            return new DiagnosisResult(answer, traceRecorder.snapshot());
        } catch (DiagnosisUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            // 模型调用是外部网络边界。这里转成业务异常，避免把供应商响应、端点或凭证相关细节直接暴露给 API 调用方。
            throw new DiagnosisUnavailableException(ExceptionMessages.MODEL_DIAGNOSIS_UNAVAILABLE, exception);
        } finally {
            // Web 线程会被线程池复用，必须清理 ThreadLocal，避免下一次请求读取到上一次诊断轨迹。
            traceRecorder.clear();
        }
    }
}

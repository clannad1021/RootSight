package kg.edu.nagisa.rootsight.agent;

import kg.edu.nagisa.rootsight.common.exception.DiagnosisUnavailableException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DiagnosisService {

    private final ChatClient chatClient;

    public DiagnosisService(ChatClient diagnosisChatClient) {
        this.chatClient = diagnosisChatClient;
    }

    public String diagnose(String question) {
        try {                          //第一步：创建AI请求
            String answer = chatClient.prompt()
                    .user(question)  //把 question 设置为用户消息
                    .call()
                    .content(); //从模型完整响应中提取最终的文本内容

            if (!StringUtils.hasText(answer)) {
                throw new DiagnosisUnavailableException("模型返回了空回答");
            }
            return answer;
        } catch (DiagnosisUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            // 模型调用是外部网络边界。这里转成业务异常，避免把供应商响应、端点或凭证相关细节直接暴露给 API 调用方。
            throw new DiagnosisUnavailableException("DeepSeek 暂时无法完成诊断", exception);
        }
    }
}

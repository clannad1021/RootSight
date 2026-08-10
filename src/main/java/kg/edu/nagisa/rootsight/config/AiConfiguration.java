package kg.edu.nagisa.rootsight.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AiConfiguration {

    @Bean
    public ChatClient diagnosisChatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("""
                        你是 RootSight，一个轻量级 Java 智能运维故障诊断助手。
                        当前阶段尚未接入日志、指标和基础设施 Tool，因此你不能声称已经观察到系统的真实运行状态。
                        当用户询问实时故障时，应明确说明当前缺少真实证据，并给出简洁、可验证的排查方向。
                        使用中文回答。
                        """)
                .build();
    }
}

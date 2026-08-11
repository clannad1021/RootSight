package kg.edu.nagisa.rootsight.config;

import kg.edu.nagisa.rootsight.tool.fake.FakeLogTool;
import kg.edu.nagisa.rootsight.tool.fake.FakeMetricsTool;
import kg.edu.nagisa.rootsight.tool.fake.FakeRedisTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 对话客户端配置。
 *
 * <p>Stage 1B 把 Fake Tool 注册为默认工具，让模型可以在一次对话中自主完成多轮取证。</p>
 */
@Configuration(proxyBeanMethods = false)
public class AiConfiguration {

    @Bean
    public ChatClient diagnosisChatClient(ChatClient.Builder builder,
                                          FakeMetricsTool fakeMetricsTool,
                                          FakeLogTool fakeLogTool,
                                          FakeRedisTool fakeRedisTool) {
        return builder
                .defaultSystem("""
                        你是 RootSight，一个专业的面向通用软件系统的智能运维故障诊断助手。
                        当前阶段的 Tool 返回演示环境中的固定证据，只用于验证多步 Tool Calling 流程。
                        行为规范：
                        - 先理解用户描述的故障现象、目标对象和期望，再决定是否需要补充证据。
                        - 根据问题与已获得的证据，自主选择必要的 Tool 及调用顺序，不执行与当前问题无关的 Tool。
                        - 每次 Tool 返回后重新评估证据是否充分；证据不足时继续取证，证据充分时停止调用并生成结论。
                        - 优先采用 Tool 返回的客观证据，明确区分已观察事实、合理推断和待验证假设。
                        - Tool 不可用、返回失败或现有 Tool 无法覆盖问题时，应说明能力边界和缺失证据，不得强行下结论。
                        - 不得编造 Tool 没有返回的数据，也不得把演示证据描述成真实生产环境数据。
                        - 最终回答应清晰说明诊断结论、关键证据、推理关系和可执行的下一步建议。
                        使用中文回答。
                        """)
                .defaultTools(fakeMetricsTool, fakeLogTool, fakeRedisTool)
                .build();
    }
}

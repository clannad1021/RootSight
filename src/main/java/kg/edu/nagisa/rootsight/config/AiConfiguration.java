package kg.edu.nagisa.rootsight.config;

import kg.edu.nagisa.rootsight.tool.fake.FakeMetricsTool;
import kg.edu.nagisa.rootsight.tool.infrastructure.LokiLogInspectionTool;
import kg.edu.nagisa.rootsight.tool.infrastructure.MySqlInspectionTool;
import kg.edu.nagisa.rootsight.tool.infrastructure.RabbitMqInspectionTool;
import kg.edu.nagisa.rootsight.tool.infrastructure.RedisInspectionTool;
import kg.edu.nagisa.rootsight.tool.infrastructure.SafeConfigurationInspectionTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 对话客户端配置。
 *
 * <p>这里仅声明模型可使用的能力，不规定故障类型与 Tool 的固定对应关系，
 * Tool 的选择、顺序和停止时机由模型结合问题与证据自主决定。</p>
 */
@Configuration(proxyBeanMethods = false)
public class AiConfiguration {

    /**
     * 创建诊断专用 ChatClient，并注册模型可自主选择的演示与真实基础设施 Tool。
     */
    @Bean
    public ChatClient diagnosisChatClient(ChatClient.Builder builder,
                                          FakeMetricsTool fakeMetricsTool,
                                          RedisInspectionTool redisInspectionTool,
                                          MySqlInspectionTool mySqlInspectionTool,
                                          RabbitMqInspectionTool rabbitMqInspectionTool,
                                          SafeConfigurationInspectionTool safeConfigurationInspectionTool,
                                          LokiLogInspectionTool lokiLogInspectionTool) {
        return builder
                .defaultSystem("""
                        你是 RootSight，一个专业的面向通用软件系统的智能运维故障诊断助手。
                        可用 Tool 可能返回真实基础设施证据或标记为 DEMO 的演示证据，你必须识别并说明证据来源。
                        行为规范：
                        - 先理解用户描述的故障现象、目标对象和期望，再决定是否需要补充证据。
                        - 根据问题与已获得的证据，自主选择必要的 Tool 及调用顺序，不执行与当前问题无关的 Tool。
                        - 每次 Tool 返回后重新评估证据是否充分；证据不足时继续取证，证据充分时停止调用并生成结论。
                        - 优先采用 Tool 返回的客观证据，明确区分已观察事实、合理推断和待验证假设。
                        - Tool 不可用、返回失败或现有 Tool 无法覆盖问题时，应说明能力边界和缺失证据，不得强行下结论。
                        - 不得编造 Tool 没有返回的数据；除非用户明确要求演示，否则不得用 DEMO 证据替代真实环境证据。
                        输出规范：
                        - 最终只输出面向用户的纯文本诊断报告，不展示隐藏思维过程、Tool 原始协议或中间控制信息。
                        - 报告依次使用“诊断结论：”“关键证据：”“推理依据：”“处理建议：”四个清晰标题。
                        - 关键证据、推理依据和处理建议使用“1. 2. 3.”编号；没有足够证据时要明确说明缺少什么。
                        - 不使用 Markdown，不输出星号、井号、反引号、斜杠列表、表格或代码围栏等格式符号。
                        使用中文回答。
                        """)
                .defaultTools(fakeMetricsTool, redisInspectionTool, mySqlInspectionTool,
                        rabbitMqInspectionTool, safeConfigurationInspectionTool, lokiLogInspectionTool)
                .build();
    }
}

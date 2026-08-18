package kg.edu.nagisa.rootsight.agent;

import kg.edu.nagisa.rootsight.agent.format.DiagnosisAnswerFormatter;
import kg.edu.nagisa.rootsight.agent.model.DiagnosisStreamEvent;
import kg.edu.nagisa.rootsight.agent.trace.ToolCallTraceRecorder;
import kg.edu.nagisa.rootsight.agent.workflow.DiagnosisWorkflowCoordinator;
import kg.edu.nagisa.rootsight.agent.workflow.DiagnosisWorkflowState;
import kg.edu.nagisa.rootsight.common.constant.ExceptionMessages;
import kg.edu.nagisa.rootsight.config.DiagnosisWorkflowProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiagnosisServiceTests {

    /**
     * 验证总诊断时限会终止始终无响应的模型流，并返回 TIMED_OUT 安全事件。
     */
    @Test
    void shouldStopDiagnosisWhenTotalTimeoutIsReached() {
        ChatClient chatClient = chatClientReturning(Flux.never());
        DiagnosisWorkflowProperties properties =
                new DiagnosisWorkflowProperties(Duration.ofMillis(30), 4);
        DiagnosisService service = service(chatClient, properties);

        List<DiagnosisStreamEvent> events = service.diagnoseStream("检查目标系统")
                .collectList()
                .block(Duration.ofSeconds(2));

        assertThat(events).isNotNull();
        assertThat(events).extracting(DiagnosisStreamEvent::type)
                .contains(DiagnosisStreamEvent.Type.STATUS, DiagnosisStreamEvent.Type.ERROR);
        DiagnosisStreamEvent error = events.stream()
                .filter(event -> event.type() == DiagnosisStreamEvent.Type.ERROR)
                .findFirst()
                .orElseThrow();
        assertThat(error.content()).isEqualTo(ExceptionMessages.DIAGNOSIS_WORKFLOW_TIMEOUT);
        assertThat(error.workflow().state()).isEqualTo(DiagnosisWorkflowState.TIMED_OUT);
    }

    /**
     * 验证正常模型正文会经历规划、归纳并最终返回 COMPLETED 工作流快照。
     */
    @Test
    void shouldCompleteControlledWorkflowWithModelContent() {
        ChatClient chatClient = chatClientReturning(Flux.just(
                "诊断结论：\n目标当前可用。\n关键证据：\n1. 已完成检查。"
        ));
        DiagnosisWorkflowProperties properties =
                new DiagnosisWorkflowProperties(Duration.ofSeconds(2), 4);
        DiagnosisService service = service(chatClient, properties);

        List<DiagnosisStreamEvent> events = service.diagnoseStream("检查目标系统")
                .collectList()
                .block(Duration.ofSeconds(2));

        assertThat(events).isNotNull();
        assertThat(events).extracting(DiagnosisStreamEvent::type)
                .contains(DiagnosisStreamEvent.Type.STATUS,
                        DiagnosisStreamEvent.Type.CONTENT,
                        DiagnosisStreamEvent.Type.COMPLETED);
        DiagnosisStreamEvent completed = events.stream()
                .filter(event -> event.type() == DiagnosisStreamEvent.Type.COMPLETED)
                .findFirst()
                .orElseThrow();
        assertThat(completed.workflow().state()).isEqualTo(DiagnosisWorkflowState.COMPLETED);
        assertThat(completed.workflow().maxToolCalls()).isEqualTo(4);
    }

    /**
     * 创建返回指定内容流的深度桩 ChatClient，隔离真实模型网络调用。
     */
    private ChatClient chatClientReturning(Flux<String> content) {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt()
                .user(anyString())
                .toolContext(anyMap())
                .stream()
                .content()).thenReturn(content);
        return chatClient;
    }

    /**
     * 使用真实状态协调器和轨迹记录器创建被测诊断服务。
     */
    private DiagnosisService service(
            ChatClient chatClient,
            DiagnosisWorkflowProperties properties
    ) {
        return new DiagnosisService(
                chatClient,
                new ToolCallTraceRecorder(),
                new DiagnosisAnswerFormatter(),
                new DiagnosisWorkflowCoordinator(properties),
                properties
        );
    }
}

package kg.edu.nagisa.rootsight.api;

import jakarta.validation.Valid;
import kg.edu.nagisa.rootsight.agent.DiagnosisService;
import kg.edu.nagisa.rootsight.api.dto.DiagnosisRequest;
import kg.edu.nagisa.rootsight.api.dto.DiagnosisResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Locale;

/**
 * 对外提供通用故障诊断入口。
 */
@RestController
@RequestMapping("/api/diagnoses")
@RequiredArgsConstructor
public class DiagnosisController {

    private final DiagnosisService diagnosisService;

    /**
     * 接收用户故障描述，并通过 SSE 持续返回模型正文和最终 Tool 调用轨迹。
     * produces = :表示返回的不是普通 JSON，而是 SSE 事件流。服务器可以保持连接，持续向客户端推送多条消息。
     * ServerSentEvent<>把消息包装成 SSE 事件
     * Flux<...>：会连续产生 0～多条 SSE 事件
     */
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<ServerSentEvent<DiagnosisResponse>> diagnose(@Valid @RequestBody DiagnosisRequest request) {
        return diagnosisService.diagnoseStream(request.question())
                .map(event -> ServerSentEvent.<DiagnosisResponse>builder()
                        //使用 Locale.ROOT 是为了让大小写转换不受服务器操作系统语言环境影响，保证结果稳定
                        .event(event.type().name().toLowerCase(Locale.ROOT))
                        .data(new DiagnosisResponse(event.type(), event.content(), event.toolCalls()))
                        .build());
    }
}

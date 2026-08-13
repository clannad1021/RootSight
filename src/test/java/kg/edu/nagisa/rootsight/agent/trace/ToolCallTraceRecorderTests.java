package kg.edu.nagisa.rootsight.agent.trace;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCallTraceRecorderTests {

    @Test
    void shouldIsolateConcurrentDiagnosisSessions() {
        ToolCallTraceRecorder recorder = new ToolCallTraceRecorder();
        String firstDiagnosisId = recorder.start();
        String secondDiagnosisId = recorder.start();

        recorder.record(context(firstDiagnosisId), "first_tool", "first evidence");
        recorder.record(context(secondDiagnosisId), "second_tool", "second evidence");

        assertThat(recorder.snapshot(firstDiagnosisId))
                .extracting(ToolCallTrace::toolName)
                .containsExactly("first_tool");
        assertThat(recorder.snapshot(secondDiagnosisId))
                .extracting(ToolCallTrace::toolName)
                .containsExactly("second_tool");
    }

    private ToolContext context(String diagnosisId) {
        return new ToolContext(Map.of(
                ToolCallTraceRecorder.DIAGNOSIS_ID_CONTEXT_KEY,
                diagnosisId
        ));
    }
}

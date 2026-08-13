package kg.edu.nagisa.rootsight.desktop.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import kg.edu.nagisa.rootsight.agent.DiagnosisService;
import kg.edu.nagisa.rootsight.agent.model.DiagnosisStreamEvent;
import kg.edu.nagisa.rootsight.agent.trace.ToolCallTrace;
import kg.edu.nagisa.rootsight.desktop.component.DesktopViewLoader;
import kg.edu.nagisa.rootsight.desktop.constant.DesktopMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.scheduler.Scheduler;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 桌面主界面控制器，负责收集用户输入、订阅流式诊断并增量渲染答案与 Tool 轨迹。
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class MainViewController {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final DiagnosisService diagnosisService;
    private final DesktopViewLoader viewLoader;
    private final Scheduler desktopTaskScheduler;

    @FXML
    private TextArea questionInput;

    @FXML
    private TextArea answerOutput;

    @FXML
    private Button diagnoseButton;

    @FXML
    private ProgressIndicator diagnosisProgress;

    @FXML
    private Label statusLabel;

    @FXML
    private Label lastRunLabel;

    @FXML
    private VBox traceContainer;

    /**
     * 初始化界面默认状态，并注册 Ctrl + Enter 快捷诊断。
     */
    @FXML
    private void initialize() {
        statusLabel.setText(DesktopMessages.READY);
        answerOutput.setText(DesktopMessages.EMPTY_RESULT);
        renderTrace(List.of());
        questionInput.setOnKeyPressed(event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.ENTER) {
                handleDiagnose();
            }
        });
    }

    /**
     * 校验问题并在后台调度器订阅诊断流，使模型每返回一段内容就立即刷新界面。
     */
    @FXML
    private void handleDiagnose() {
        String question = questionInput.getText() == null ? "" : questionInput.getText().trim();
        if (!StringUtils.hasText(question)) {
            showInlineError(DesktopMessages.EMPTY_QUESTION);
            return;
        }

        setBusy(true);
        answerOutput.clear();
        renderTrace(List.of());

        diagnosisService.diagnoseStream(question)
                .subscribeOn(desktopTaskScheduler)
                .subscribe(
                        event -> Platform.runLater(() -> renderStreamEvent(event)),
                        throwable -> Platform.runLater(this::handleUnexpectedStreamFailure)
                );
    }

    /**
     * 填入一个通用的延迟诊断示例，用户仍可继续编辑。
     */
    @FXML
    private void useLatencyPrompt() {
        questionInput.setText("演示环境中的 order-service 最近响应很慢，请主动收集证据并诊断根因。");
        questionInput.requestFocus();
        questionInput.positionCaret(questionInput.getLength());
    }

    /**
     * 填入一个通用的错误率排查示例。
     */
    @FXML
    private void useErrorPrompt() {
        questionInput.setText("payment-service 的请求错误率突然升高，请检查可能的组件故障并给出处理建议。");
        questionInput.requestFocus();
        questionInput.positionCaret(questionInput.getLength());
    }

    /**
     * 打开只展示非敏感运行配置的桌面设置窗口。
     */
    @FXML
    private void openSettings() {
        try {
            Parent root = viewLoader.load("/desktop/fxml/settings-view.fxml");
            Stage settingsStage = new Stage();
            settingsStage.initOwner(questionInput.getScene().getWindow());
            settingsStage.initModality(Modality.WINDOW_MODAL);
            settingsStage.setTitle("RootSight · 客户端设置");
            settingsStage.setResizable(false);
            settingsStage.setScene(new Scene(root));
            settingsStage.showAndWait();
        } catch (IOException exception) {
            showInlineError(DesktopMessages.SETTINGS_LOAD_FAILED);
        }
    }

    /**
     * 根据流式事件增量追加正文，或在终止事件到达时更新状态与 Tool 轨迹。
     */
    private void renderStreamEvent(DiagnosisStreamEvent event) {
        switch (event.type()) {
            case CONTENT -> answerOutput.appendText(event.content());
            case COMPLETED -> finishDiagnosis(event.toolCalls());
            case ERROR -> failDiagnosis(event.content(), event.toolCalls());
        }
    }

    /**
     * 正常结束一次流式诊断，并展示本次实际执行的 Tool 轨迹。
     */
    private void finishDiagnosis(List<ToolCallTrace> traces) {
        renderTrace(traces);
        setBusy(false);
        statusLabel.setText(DesktopMessages.FINISHED);
        lastRunLabel.setText("完成于 " + LocalTime.now().format(TIME_FORMATTER));
    }

    /**
     * 处理服务通过 ERROR 事件返回的安全错误消息，同时保留失败前已经取得的 Tool 轨迹。
     */
    private void failDiagnosis(String message, List<ToolCallTrace> traces) {
        renderTrace(traces);
        setBusy(false);
        showInlineError(StringUtils.hasText(message) ? message : DesktopMessages.UNKNOWN_FAILURE);
    }

    /**
     * 把结构化 Tool 轨迹渲染为独立证据卡片。
     */
    private void renderTrace(List<ToolCallTrace> traces) {
        traceContainer.getChildren().clear();
        if (traces == null || traces.isEmpty()) {
            Label emptyLabel = new Label(DesktopMessages.EMPTY_TRACE);
            emptyLabel.getStyleClass().add("trace-empty");
            traceContainer.getChildren().add(emptyLabel);
            return;
        }

        for (int index = 0; index < traces.size(); index++) {
            ToolCallTrace trace = traces.get(index);
            Label indexLabel = new Label(String.format("%02d", index + 1));
            indexLabel.getStyleClass().add("trace-index");

            Label nameLabel = new Label(toDisplayName(trace.toolName()));
            nameLabel.getStyleClass().add("trace-name");
            Label summaryLabel = new Label(trace.summary());
            summaryLabel.setWrapText(true);
            summaryLabel.getStyleClass().add("trace-summary");

            VBox content = new VBox(5, nameLabel, summaryLabel);
            HBox traceCard = new HBox(12, indexLabel, content);
            traceCard.getStyleClass().add("trace-card");
            traceContainer.getChildren().add(traceCard);
        }
    }

    /**
     * 切换诊断按钮、加载动画和状态文本。
     */
    private void setBusy(boolean busy) {
        diagnoseButton.setDisable(busy);
        diagnosisProgress.setVisible(busy);
        diagnosisProgress.setManaged(busy);
        statusLabel.setText(busy ? DesktopMessages.RUNNING : DesktopMessages.READY);
    }

    /**
     * 在结果区显示用户可理解的错误，不暴露底层模型异常细节。
     */
    private void showInlineError(String message) {
        answerOutput.setText(message);
        statusLabel.setText(DesktopMessages.FAILED);
    }

    /**
     * 处理未被诊断服务转成 ERROR 事件的意外流错误，不向界面暴露底层实现细节。
     */
    private void handleUnexpectedStreamFailure() {
        setBusy(false);
        showInlineError(DesktopMessages.UNKNOWN_FAILURE);
    }

    /**
     * 把内部 Tool 方法名转换为适合桌面展示的名称。
     */
    private String toDisplayName(String toolName) {
        return switch (toolName) {
            case "query_service_http_metrics" -> "HTTP 指标采样";
            case "query_recent_error_logs" -> "异常日志检索";
            case "check_redis_health" -> "Redis 健康检查";
            case "inspect_mysql_status" -> "MySQL 只读状态检查";
            case "inspect_redis_status" -> "Redis 只读状态检查";
            default -> toolName;
        };
    }
}

package kg.edu.nagisa.rootsight.desktop.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 设置窗口控制器，只展示允许公开的模型配置和密钥加载状态。
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class SettingsViewController {

    private final Environment environment;

    @FXML
    private Label modelValue;

    @FXML
    private Label baseUrlValue;

    @FXML
    private Label retryValue;

    @FXML
    private Label apiKeyValue;

    /**
     * 读取 Spring 已解析的非敏感配置；API Key 只判断是否存在，绝不显示实际内容。
     */
    @FXML
    private void initialize() {
        modelValue.setText(environment.getProperty(
                "spring.ai.deepseek.chat.model",
                "deepseek-v4-flash"
        ));
        baseUrlValue.setText(environment.getProperty(
                "spring.ai.deepseek.base-url",
                "https://api.deepseek.com"
        ));
        retryValue.setText(environment.getProperty(
                "spring.ai.retry.max-attempts",
                "2"
        ) + " 次");

        String apiKey = environment.getProperty("spring.ai.deepseek.api-key");
        apiKeyValue.setText(StringUtils.hasText(apiKey) ? "已安全加载" : "尚未配置");
        apiKeyValue.getStyleClass().add(StringUtils.hasText(apiKey)
                ? "config-ok"
                : "config-warning");
    }

    /**
     * 关闭当前设置窗口。
     */
    @FXML
    private void closeWindow(ActionEvent event) {
        ((Node) event.getSource()).getScene().getWindow().hide();
    }
}

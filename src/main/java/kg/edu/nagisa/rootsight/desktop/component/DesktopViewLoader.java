package kg.edu.nagisa.rootsight.desktop.component;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import kg.edu.nagisa.rootsight.desktop.constant.DesktopMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;

/**
 * 统一加载 FXML，并让 FXMLLoader 从 Spring 容器取得 Controller。
 */
@Component
@RequiredArgsConstructor
public class DesktopViewLoader {

    private final ApplicationContext applicationContext;

    /**
     * 加载指定 FXML 视图。
     *
     * @param resourcePath classpath 下的 FXML 绝对路径
     * @return 已完成 Spring Controller 注入的 JavaFX 根节点
     */
    public Parent load(String resourcePath) throws IOException {
        URL resource = Objects.requireNonNull(
                getClass().getResource(resourcePath),
                DesktopMessages.fxmlMissing(resourcePath)
        );
        FXMLLoader loader = new FXMLLoader(resource);
        loader.setControllerFactory(applicationContext::getBean);
        return loader.load();
    }
}

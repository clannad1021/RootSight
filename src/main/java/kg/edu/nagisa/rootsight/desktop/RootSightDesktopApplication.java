package kg.edu.nagisa.rootsight.desktop;

import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import kg.edu.nagisa.rootsight.RootSightApplication;
import kg.edu.nagisa.rootsight.desktop.component.DesktopViewLoader;
import kg.edu.nagisa.rootsight.desktop.constant.DesktopMessages;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Objects;

/**
 * RootSight 桌面客户端生命周期入口。
 *
 * <p>JavaFX 负责窗口生命周期，Spring 仍负责 Controller、DiagnosisService 和 Tool 的依赖注入。</p>
 */
public class RootSightDesktopApplication extends Application {

    private ConfigurableApplicationContext applicationContext;

    /**
     * 在 JavaFX 创建窗口前启动无 Web Server 的 Spring 容器。
     */
    @Override
    public void init() {
        applicationContext = new SpringApplicationBuilder(RootSightApplication.class)
                .web(WebApplicationType.NONE)
                .headless(false)
                .run(getParameters().getRaw().toArray(String[]::new));
    }

    /**
     * 加载主视图并配置桌面窗口尺寸。
     */
    @Override
    public void start(Stage primaryStage) throws Exception {
        DesktopViewLoader viewLoader = applicationContext.getBean(DesktopViewLoader.class);
        Parent root = viewLoader.load("/desktop/fxml/main-view.fxml");

        Scene scene = new Scene(root, 1380, 860);
        primaryStage.setTitle("RootSight · 智能诊断终端");
        primaryStage.setMinWidth(1160);
        primaryStage.setMinHeight(720);
        primaryStage.setScene(scene);
        primaryStage.getIcons().add(new Image(Objects.requireNonNull(
                getClass().getResourceAsStream("/desktop/images/blue-archive-cast.png"),
                DesktopMessages.MAIN_ART_MISSING
        )));
        primaryStage.show();
    }

    /**
     * 窗口退出时关闭 Spring 容器，释放诊断线程池和模型客户端资源。
     */
    @Override
    public void stop() {
        if (applicationContext != null) {
            applicationContext.close();
        }
    }
}

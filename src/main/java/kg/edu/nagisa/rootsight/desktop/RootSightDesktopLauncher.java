package kg.edu.nagisa.rootsight.desktop;

import javafx.application.Application;

/**
 * JavaFX 独立启动器。
 *
 * <p>单独保留普通 main 类，可以避开某些 JVM 对 Application 子类启动方式的特殊处理。</p>
 */
public final class RootSightDesktopLauncher {

    /**
     * 通过普通 Java 主类启动 JavaFX 桌面应用。
     */
    public static void main(String[] args) {
        Application.launch(RootSightDesktopApplication.class, args);
    }
}

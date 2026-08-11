package kg.edu.nagisa.rootsight.desktop.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 桌面客户端专用基础设施配置。
 */
@Configuration(proxyBeanMethods = false)
public class DesktopConfiguration {

    /**
     * 创建单线程后台执行器，避免同步模型调用阻塞 JavaFX UI 线程。
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService desktopTaskExecutor() {
        return Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "rootsight-desktop-diagnosis");
            thread.setDaemon(true);
            return thread;
        });
    }
}

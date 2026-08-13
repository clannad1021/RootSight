package kg.edu.nagisa.rootsight.desktop.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * 桌面客户端专用基础设施配置。
 */
@Configuration(proxyBeanMethods = false)
public class DesktopConfiguration {

    /**
     * 创建桌面诊断专用调度器，保证订阅和流式回调不会占用 JavaFX UI 线程。
     */
    @Bean(destroyMethod = "dispose")
    public Scheduler desktopTaskScheduler() {
        return Schedulers.newSingle("rootsight-desktop-diagnosis", true);
    }
}

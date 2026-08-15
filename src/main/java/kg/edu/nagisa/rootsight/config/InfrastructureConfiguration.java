package kg.edu.nagisa.rootsight.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 注册基础设施目标配置。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        InfrastructureTargetProperties.class,
        RabbitMqManagementProperties.class,
        LokiProperties.class
})
public class InfrastructureConfiguration {
}

package kg.edu.nagisa.rootsight.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * RabbitMQ Management HTTP API 的只读访问配置。
 *
 * <p>密码只由环境变量注入；配置对象不会作为 Tool 证据返回给模型。</p>
 */
@ConfigurationProperties(prefix = "rootsight.rabbitmq.management")
public record RabbitMqManagementProperties(
        String url,
        String username,
        String password,
        String vhost,
        int queuePageSize,
        int queueSampleLimit,
        Duration connectTimeout,
        Duration readTimeout
) {
}

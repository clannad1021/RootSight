package kg.edu.nagisa.rootsight.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Prometheus 只读查询和安全参数边界配置。
 *
 * <p>模型只能提供服务标签值、故障时间和白名单窗口，不能覆盖 API 地址或提交 PromQL。</p>
 */
@ConfigurationProperties(prefix = "rootsight.prometheus")
public record PrometheusProperties(
        String url,
        String serviceLabel,
        String defaultService,
        String defaultWindow,
        List<String> allowedWindows,
        int maxServiceLength,
        Duration queryTimeout,
        Duration connectTimeout,
        Duration readTimeout
) {
}

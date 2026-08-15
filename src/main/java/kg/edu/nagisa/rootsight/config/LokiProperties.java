package kg.edu.nagisa.rootsight.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Loki 只读查询和有界扩窗策略配置。
 *
 * <p>服务标签名由运维侧配置，模型只能提供标签值、时间锚点和有限文本过滤条件。</p>
 */
@ConfigurationProperties(prefix = "rootsight.loki")
public record LokiProperties(
        String url,
        String serviceLabel,
        String defaultService,
        int defaultLimit,
        int maxLimit,
        int maxFilterLength,
        int maxLineLength,
        Duration defaultWindow,
        List<Duration> expansionWindows,
        Duration fallbackWindow,
        Duration incidentBefore,
        Duration incidentAfter,
        Duration contextWindow,
        int contextLimit,
        Duration connectTimeout,
        Duration readTimeout
) {
}

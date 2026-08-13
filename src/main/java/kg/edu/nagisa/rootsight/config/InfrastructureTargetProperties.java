package kg.edu.nagisa.rootsight.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 当前 RootSight 实例所观察的基础设施目标。
 *
 * <p>Stage 2A 先支持一个活动目标，但 Tool 和证据不绑定任何业务项目名称。</p>
 */
@ConfigurationProperties(prefix = "rootsight.target")
public record InfrastructureTargetProperties(String name) {
}

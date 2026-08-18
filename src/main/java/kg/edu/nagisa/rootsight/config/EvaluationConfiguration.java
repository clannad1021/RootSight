package kg.edu.nagisa.rootsight.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 注册 Evaluation 的资源边界与默认评分配置。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EvaluationProperties.class)
public class EvaluationConfiguration {
}

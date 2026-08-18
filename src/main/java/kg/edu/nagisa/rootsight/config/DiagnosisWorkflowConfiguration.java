package kg.edu.nagisa.rootsight.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 注册诊断工作流的超时与 Tool 预算配置。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DiagnosisWorkflowProperties.class)
public class DiagnosisWorkflowConfiguration {
}

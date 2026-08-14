package kg.edu.nagisa.rootsight.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 创建访问 RabbitMQ Management HTTP API 的专用只读客户端。
 */
@Configuration(proxyBeanMethods = false)
public class RabbitMqManagementConfiguration {

    /**
     * 创建带基础认证和短超时的 RabbitMQ Management API 客户端，避免目标故障长时间阻塞诊断。
     */
    @Bean
    public RestClient rabbitMqManagementRestClient(RabbitMqManagementProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        return RestClient.builder()
                .baseUrl(properties.url())
                .requestFactory(requestFactory)
                .defaultHeaders(headers -> headers.setBasicAuth(properties.username(), properties.password()))
                .build();
    }
}

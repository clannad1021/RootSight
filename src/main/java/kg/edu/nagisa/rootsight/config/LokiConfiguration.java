package kg.edu.nagisa.rootsight.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 创建访问 Loki HTTP API 的专用只读客户端。
 */
@Configuration(proxyBeanMethods = false)
public class LokiConfiguration {

    /**
     * 创建带连接和读取超时的 Loki 客户端，避免日志系统故障长时间阻塞 Agent 诊断。
     */
    @Bean
    public RestClient lokiRestClient(LokiProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.url())
                .requestFactory(requestFactory)
                .build();
    }
}

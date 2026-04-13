package com.wwa.agenthub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate configuration for outbound HTTP calls to Jenkins/Ansible.
 *
 * <p>Timeouts:
 * <ul>
 *   <li>Connect timeout: 10 seconds — enough for internal network, fails fast on unreachable hosts</li>
 *   <li>Read timeout: 30 seconds — Jenkins/Ansible may take time to queue a job, but should not block indefinitely</li>
 * </ul>
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        return new RestTemplate(factory);
    }
}

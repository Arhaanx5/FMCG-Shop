package com.shop.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Global application configuration.
 * Declares bean definitions for HTTP clients to prevent connection leaks
 * and establish safe client read/connect timeouts.
 */
@Configuration
public class AppConfig {

    /**
     * Default RestTemplate configured with 45-second connect/read timeouts.
     * Primary bean used for standard outbound HTTP calls (OCR, WhatsApp, etc.).
     *
     * @return pre-configured RestTemplate instance
     */
    @Bean
    @Primary
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(45000);
        factory.setReadTimeout(45000);
        return new RestTemplate(factory);
    }

    /**
     * Short-timeout RestTemplate configured with 4s connect and 6s read timeouts.
     * Designated specifically for short-lived AI completions (e.g. Gemini).
     *
     * @return pre-configured short-timeout RestTemplate instance
     */
    @Bean(name = "shortTimeoutRestTemplate")
    public RestTemplate shortTimeoutRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(4000);
        factory.setReadTimeout(6000);
        return new RestTemplate(factory);
    }

    /**
     * Dedicated RestTemplate bean for WhatsApp operations to isolate custom interceptors.
     */
    @Bean(name = "whatsAppRestTemplate")
    public RestTemplate whatsAppRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(45000);
        factory.setReadTimeout(45000);
        return new RestTemplate(factory);
    }
}

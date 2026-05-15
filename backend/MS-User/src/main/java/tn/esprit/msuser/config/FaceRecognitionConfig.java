package tn.esprit.msuser.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration for Face Recognition Service integration
 * 
 * Configures RestTemplate for calling external face recognition AI service
 * with appropriate timeouts, retries, and error handling
 */
@Configuration
@Slf4j
public class FaceRecognitionConfig {

    @Value("${face.recognition.service.url:http://localhost:5050}")
    private String faceServiceUrl;

    @Value("${face.recognition.service.timeout.read:30000}")
    private int readTimeout;

    @Value("${face.recognition.service.timeout.connect:10000}")
    private int connectTimeout;

    @Value("${face.recognition.verify.endpoint:/verify}")
    private String verifyEndpoint;

    @Value("${face.recognition.register.endpoint:/register}")
    private String registerEndpoint;

    @Value("${face.recognition.confidence.threshold:0.85}")
    private double confidenceThreshold;

    @Value("${face.recognition.max.retries:3}")
    private int maxRetries;

    @Value("${face.recognition.retry.delay.ms:1000}")
    private int retryDelayMs;

    /**
     * RestTemplate Bean for synchronous HTTP calls to Face Recognition Service
     * Used for calling external face recognition AI service
     */
    @Bean
    public RestTemplate faceRecognitionRestTemplate() {
        log.info("Initializing Face Recognition RestTemplate with timeout: connect={}ms, read={}ms", 
                connectTimeout, readTimeout);
        
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setRequestFactory(clientHttpRequestFactory());
        return restTemplate;
    }

    /**
     * Client HTTP Request Factory with buffering capability
     * Allows request/response logging and retry capabilities
     */
    private ClientHttpRequestFactory clientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return new BufferingClientHttpRequestFactory(factory);
    }

    /**
     * Getter methods for configuration values
     * Used by FaceRecognitionService
     */
    public String getFaceServiceUrl() {
        return faceServiceUrl;
    }

    public String getVerifyEndpoint() {
        return verifyEndpoint;
    }

    public String getRegisterEndpoint() {
        return registerEndpoint;
    }

    public double getConfidenceThreshold() {
        return confidenceThreshold;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public int getRetryDelayMs() {
        return retryDelayMs;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }
}

package tn.esprit.msassessment.integration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(UserServiceIntegrationProperties.class)
public class IntegrationConfiguration {}

package tn.esprit.msassessment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class MsAssessmentApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsAssessmentApplication.class, args);
    }
}



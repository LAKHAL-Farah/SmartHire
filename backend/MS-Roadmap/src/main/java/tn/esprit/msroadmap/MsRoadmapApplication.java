package tn.esprit.msroadmap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableDiscoveryClient
@EnableJpaAuditing
public class MsRoadmapApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsRoadmapApplication.class, args);
    }

}

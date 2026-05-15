package tn.esprit.msprofile;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class MsProfileApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsProfileApplication.class, args);
    }
}


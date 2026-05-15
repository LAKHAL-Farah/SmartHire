package tn.esprit.msinterview.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "live")
@Data
public class LiveModeScripts {

    private Greeting greeting = new Greeting();
    private Warmup warmup = new Warmup();
    private List<String> transitions = new ArrayList<>();
    private List<String> fillers = new ArrayList<>();
    private List<String> closing = new ArrayList<>();
    private List<String> retryPrompts = new ArrayList<>();

    @Data
    public static class Greeting {
        private String template = "";
    }

    @Data
    public static class Warmup {
        private String practice = "";
        private String test = "";
    }
}

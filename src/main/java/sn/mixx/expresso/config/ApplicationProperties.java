package sn.mixx.expresso.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "application")
@Data
public class ApplicationProperties {

    private final Sms sms = new Sms();

    @Data
    public static class Sms {
        private String apiUrl;
        private String sender = "MIXX-EXPRESSO";
    }
}
package sn.mixx.expresso.service.mfa;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import sn.mixx.expresso.config.ApplicationProperties;
import sn.mixx.expresso.service.dto.mfa.SmsRequest;

import javax.net.ssl.SSLException;

@Service
public class SmsService {

    private static final Logger LOG = LoggerFactory.getLogger(SmsService.class);

    private final WebClient webClient;
    private final String smsSender;

    public SmsService(ApplicationProperties applicationProperties) throws SSLException {
        SslContext sslContext = SslContextBuilder
            .forClient()
            .trustManager(InsecureTrustManagerFactory.INSTANCE)
            .build();

        HttpClient httpClient = HttpClient.create()
            .secure(t -> t.sslContext(sslContext));

        this.webClient = WebClient.builder()
            .baseUrl(applicationProperties.getSms().getApiUrl())
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
        this.smsSender = applicationProperties.getSms().getSender();
    }

    public Mono<Boolean> sendOtp(String phoneNumber, String otpCode) {
        String message = String.format("Votre code de verification est: %s. Valide pendant 5 minutes.", otpCode);
        return sendSms(phoneNumber, message);
    }

    public Mono<Boolean> sendSms(String phoneNumber, String message) {
        SmsRequest request = new SmsRequest(phoneNumber, message, smsSender, 1);

        LOG.debug("Envoi SMS au {}: {}", maskPhoneNumber(phoneNumber), message);

        return webClient
            .post()
            .uri("/services/SendNotifications.aspx")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(String.class)
            .map(response -> {
                LOG.debug("Réponse SMS API: {}", response);
                return true;
            })
            .onErrorResume(error -> {
                LOG.error("Erreur lors de l'envoi du SMS: {}", error.getMessage());
                return Mono.just(false);
            });
    }

    public String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) return "***";
        int visibleDigits = 3;
        String masked = "*".repeat(phoneNumber.length() - visibleDigits);
        return masked + phoneNumber.substring(phoneNumber.length() - visibleDigits);
    }
}
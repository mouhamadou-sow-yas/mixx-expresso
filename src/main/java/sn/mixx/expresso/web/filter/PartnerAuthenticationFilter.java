package sn.mixx.expresso.web.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import sn.mixx.expresso.security.PartnerSecureService;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PartnerAuthenticationFilter implements WebFilter {

    private final PartnerSecureService partnerSecureService;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (!path.startsWith("/v1/transactions")) {
            return chain.filter(exchange);
        }

        var headers = exchange.getRequest().getHeaders();
        String codePartner = headers.getFirst("X-YAS-Partner-Code");
        String apiKey = headers.getFirst("X-YAS-API-Key");
        String apiSecret = headers.getFirst("X-YAS-API-Secret");

        if (codePartner == null || apiKey == null || apiSecret == null) {
            log.warn("[PARTNER-AUTH] Headers d'authentification manquants pour: {}", path);
            return sendUnauthorized(exchange, "Headers d'authentification manquants");
        }

        return partnerSecureService.validatePartnerCredentials(codePartner, apiKey, apiSecret)
            .flatMap(isValid -> {
                if (!isValid) {
                    log.warn("[PARTNER-AUTH] Credentials invalides pour: {}", codePartner);
                    return sendUnauthorized(exchange, "Credentials invalides");
                }
                log.debug("[PARTNER-AUTH] Authentification réussie pour: {}", codePartner);
                return chain.filter(exchange);
            })
            .onErrorResume(error -> {
                log.error("[PARTNER-AUTH] Erreur authentification pour: {}", codePartner, error);
                return sendUnauthorized(exchange, "Erreur d'authentification");
            });
    }

    private Mono<Void> sendUnauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(Map.of(
                "status", 401,
                "title", "Unauthorized",
                "detail", message
            ));
        } catch (JsonProcessingException e) {
            return exchange.getResponse().setComplete();
        }

        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse()
            .writeWith(Mono.just(buffer))
            .doOnDiscard(DataBuffer.class, DataBufferUtils::release);
    }
}
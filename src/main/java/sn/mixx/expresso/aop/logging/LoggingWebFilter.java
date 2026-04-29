package sn.mixx.expresso.aop.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LoggingWebFilter implements WebFilter {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingWebFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String requestId = request.getHeaders().getFirst("X-Request-ID");
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        String actionId = UUID.randomUUID().toString();
        String path = request.getPath().value();
        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
        String finalRequestId = requestId;
        long startTime = Instant.now().toEpochMilli();

        LOG.debug("Incoming request: {} {} - RequestId: {}", method, path, requestId);

        return chain.filter(exchange)
            .doFinally(signalType -> {
                long duration = Instant.now().toEpochMilli() - startTime;
                int statusCode = exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().value() : 0;
                LOG.info("Request completed: {} {} - Status: {} - Duration: {}ms - RequestId: {}",
                    method, path, statusCode, duration, finalRequestId);
            })
            .doOnError(error -> {
                long duration = Instant.now().toEpochMilli() - startTime;
                LOG.error("Request failed: {} {} - Duration: {}ms - RequestId: {} - Error: {}",
                    method, path, duration, finalRequestId, error.getMessage());
            });
    }
}
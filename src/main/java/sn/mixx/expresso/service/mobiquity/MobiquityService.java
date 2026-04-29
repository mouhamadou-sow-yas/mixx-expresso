package sn.mixx.expresso.service.mobiquity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import sn.mixx.expresso.exception.InsufficientBalanceException;
import sn.mixx.expresso.exception.MobiquityDebitException;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class MobiquityService {

    private final WebClient.Builder webClientMobiquity;

    @Value("${mobiquity.timeout:30}")
    private int timeoutSeconds;

    @Value("${mobiquity.url_cancel_tx}")
    private String cancelTxUrl;

    public Mono<BigDecimal> getAccountBalance(String msisdn) {
        log.debug("[MOBIQUITY] Vérification solde: {}", maskMsisdn(msisdn));

        return webClientMobiquity.build()
            .post()
            .uri("/jigsaw/serviceRequest/BALANCE")
            .bodyValue(Map.of("msisdn", msisdn))
            .retrieve()
            .bodyToMono(Map.class)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .map(response -> {
                Object balance = response.get("balance");
                if (balance == null) throw new MobiquityDebitException("Solde introuvable pour " + maskMsisdn(msisdn));
                return new BigDecimal(balance.toString());
            })
            .retryWhen(Retry.backoff(2, Duration.ofSeconds(2))
                .filter(t -> t instanceof ConnectException ||
                             t instanceof org.springframework.web.reactive.function.client.WebClientRequestException)
            )
            .doOnError(error -> log.error("[MOBIQUITY] Erreur vérification solde: {}", error.getMessage()));
    }

    public Mono<String> debitAccount(String txnId, String msisdn, BigDecimal amount) {
        log.info("[MOBIQUITY] Débit compte: msisdn={}, amount={}, txnId={}", maskMsisdn(msisdn), amount, txnId);

        return webClientMobiquity.build()
            .post()
            .uri("/jigsaw/sync/serviceRequest/MERCHPAY")
            .bodyValue(Map.of(
                "transactionId", txnId,
                "msisdn", msisdn,
                "amount", amount.toString()
            ))
            .retrieve()
            .bodyToMono(Map.class)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .flatMap(response -> {
                String status = (String) response.get("status");
                if ("SUCCESS".equalsIgnoreCase(status)) {
                    String debitRef = (String) response.get("reference");
                    log.info("[MOBIQUITY] Débit réussi: ref={}, txnId={}", debitRef, txnId);
                    return Mono.just(debitRef);
                } else {
                    String errorMsg = (String) response.getOrDefault("message", "Échec du débit");
                    log.warn("[MOBIQUITY] Débit échoué: {}, txnId={}", errorMsg, txnId);
                    return Mono.error(new MobiquityDebitException(errorMsg));
                }
            })
            .retryWhen(Retry.backoff(2, Duration.ofSeconds(2))
                .filter(t -> t instanceof ConnectException ||
                             t instanceof org.springframework.web.reactive.function.client.WebClientRequestException)
            )
            .onErrorMap(TimeoutException.class, t ->
                new MobiquityDebitException("Timeout débit Mobiquity pour txnId=" + txnId));
    }

    public Mono<String> refundAccount(String txnId, String msisdn, BigDecimal amount) {
        log.info("[MOBIQUITY] Remboursement MANUEL: msisdn={}, amount={}, txnId={}", maskMsisdn(msisdn), amount, txnId);

        return webClientMobiquity.build()
            .post()
            .uri(cancelTxUrl)
            .bodyValue(Map.of(
                "transactionId", txnId,
                "msisdn", msisdn,
                "amount", amount.toString()
            ))
            .retrieve()
            .bodyToMono(Map.class)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .map(response -> {
                String refundRef = (String) response.getOrDefault("reference", txnId + "-REFUND");
                log.info("[MOBIQUITY] Remboursement réussi: ref={}, txnId={}", refundRef, txnId);
                return refundRef;
            })
            .retryWhen(Retry.backoff(2, Duration.ofSeconds(2))
                .filter(t -> t instanceof ConnectException ||
                             t instanceof org.springframework.web.reactive.function.client.WebClientRequestException)
            );
    }

    public Mono<String> checkDebitStatus(String txnId) {
        log.debug("[MOBIQUITY] Vérification statut débit: txnId={}", txnId);

        return webClientMobiquity.build()
            .post()
            .uri("/jigsaw/serviceRequest/checkStatus")
            .bodyValue(Map.of("idempotencyRef", txnId))
            .retrieve()
            .bodyToMono(Map.class)
            .timeout(Duration.ofSeconds(10))
            .map(response -> (String) response.getOrDefault("status", "UNKNOWN"));
    }

    private String maskMsisdn(String msisdn) {
        if (msisdn == null || msisdn.length() < 8) return "***";
        return msisdn.substring(0, 3) + "****" + msisdn.substring(msisdn.length() - 2);
    }
}
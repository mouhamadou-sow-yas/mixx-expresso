package sn.mixx.expresso.service.expresso;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import sn.mixx.expresso.domain.elastic.ExpressoCallDocument;
import sn.mixx.expresso.exception.ExpressoBusinessFailureException;
import sn.mixx.expresso.exception.ExpressoTimeoutException;
import sn.mixx.expresso.exception.ExpressoUnavailableException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Adapter pour l'API SOAP Expresso ERS.
 * Gère : requestTopup, getTransactionStatus, requestPrincipalInformation.
 *
 * Règles critiques :
 * - Timeout → ExpressoTimeoutException → transaction en état PENDING + retry scheduler
 * - resultCode != 0 définitif → ExpressoBusinessFailureException → FAILED (pas de refund auto)
 * - Connexion impossible → ExpressoUnavailableException → PENDING + retry
 * - L'idempotence est garantie par clientReference (même référence en cas de retry)
 * - JAMAIS re-jouer requestTopup sans vérification préalable via getTransactionStatus
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpressoAdapter {

    private final ExpressoCallLogService expressoCallLogService;

    @Value("${expresso.url_ers}")
    private String ersUrl;

    @Value("${expresso.initiator_principal_id}")
    private String initiatorPrincipalId;

    @Value("${expresso.initiator_password}")
    private String initiatorPassword;

    @Value("${expresso.client_id:Transfert2Com}")
    private String clientId;

    @Value("${expresso.timeout_ms:15000}")
    private int timeoutMs;

    /**
     * Effectue la recharge airtime ou l'activation de bundle via requestTopup.
     * En cas de timeout ou d'absence de réponse, lève ExpressoTimeoutException
     * (la transaction doit passer en PENDING, pas de retry immédiat de requestTopup).
     *
     * @param txnId          UUID de la transaction Mixx (idempotency key côté ERS)
     * @param clientReference clé d'idempotence propagée à Expresso
     * @param beneficiaryMsisdn MSISDN Expresso à recharger
     * @param amount          montant en FCFA
     * @param productId       ID produit ERS (null pour airtime)
     * @param correlationId   ID de corrélation pour le logging
     * @return référence ERS (ersReference) si succès
     */
    public Mono<String> requestTopup(String txnId, String clientReference,
                                     String beneficiaryMsisdn, BigDecimal amount,
                                     String productId, String correlationId) {
        log.info("[ERS] requestTopup: txnId={}, msisdn={}, amount={}, product={}",
            txnId, maskMsisdn(beneficiaryMsisdn), amount, productId);

        long start = System.currentTimeMillis();

        return Mono.fromCallable(() -> callRequestTopup(txnId, clientReference, beneficiaryMsisdn, amount, productId))
            .subscribeOn(Schedulers.boundedElastic())
            .timeout(java.time.Duration.ofMillis(timeoutMs))
            .flatMap(result -> {
                long duration = System.currentTimeMillis() - start;
                logExpressoCall(correlationId, txnId, clientReference, "requestTopup",
                    result.resultCode, result.ersReference, duration, null);

                if (result.resultCode == 0) {
                    log.info("[ERS] requestTopup SUCCESS: ersRef={}, txnId={}", result.ersReference, txnId);
                    return Mono.just(result.ersReference);
                } else {
                    log.warn("[ERS] requestTopup BUSINESS FAILURE: code={}, desc={}, txnId={}",
                        result.resultCode, result.description, txnId);
                    return Mono.error(new ExpressoBusinessFailureException(
                        "Expresso ERS erreur: " + result.description, result.resultCode));
                }
            })
            .onErrorMap(TimeoutException.class, t -> {
                log.warn("[ERS] requestTopup TIMEOUT après {}ms: txnId={}", timeoutMs, txnId);
                logExpressoCall(correlationId, txnId, clientReference, "requestTopup",
                    null, null, System.currentTimeMillis() - start, "TIMEOUT");
                return new ExpressoTimeoutException("Timeout requestTopup ERS après " + timeoutMs + "ms");
            })
            .onErrorMap(java.net.ConnectException.class, t -> {
                log.error("[ERS] requestTopup CONNEXION IMPOSSIBLE: txnId={}, cause={}", txnId, t.getMessage());
                return new ExpressoUnavailableException("Expresso ERS indisponible", t);
            });
    }

    /**
     * Vérifie le statut d'une transaction Expresso via getTransactionStatus.
     * Utilisé EXCLUSIVEMENT par le retry scheduler — jamais pour rejouer requestTopup.
     *
     * @param clientReference clé d'idempotence originale de la transaction
     * @param correlationId   ID de corrélation
     * @return "SUCCESS", "FAILED", ou "PENDING"
     */
    public Mono<String> getTransactionStatus(String clientReference, String correlationId) {
        log.debug("[ERS] getTransactionStatus: clientRef={}", clientReference);

        long start = System.currentTimeMillis();

        return Mono.fromCallable(() -> callGetTransactionStatus(clientReference))
            .subscribeOn(Schedulers.boundedElastic())
            .timeout(java.time.Duration.ofMillis(timeoutMs))
            .map(result -> {
                long duration = System.currentTimeMillis() - start;
                logExpressoCall(correlationId, null, clientReference, "getTransactionStatus",
                    result.resultCode, null, duration, null);

                if (result.resultCode == 0) return "SUCCESS";
                if (isDefinitiveFailureCode(result.resultCode)) return "FAILED";
                return "PENDING";
            })
            .onErrorResume(TimeoutException.class, t -> {
                log.warn("[ERS] getTransactionStatus TIMEOUT: clientRef={}", clientReference);
                return Mono.just("PENDING");
            })
            .onErrorResume(t -> {
                log.error("[ERS] getTransactionStatus ERROR: clientRef={}, cause={}", clientReference, t.getMessage());
                return Mono.just("PENDING");
            });
    }

    /**
     * Consulte le solde du compte dealer Expresso via requestPrincipalInformation.
     */
    public Mono<BigDecimal> getDealerBalance(String correlationId) {
        log.debug("[ERS] requestPrincipalInformation: solde dealer");

        return Mono.fromCallable(this::callRequestPrincipalInformation)
            .subscribeOn(Schedulers.boundedElastic())
            .timeout(java.time.Duration.ofMillis(timeoutMs))
            .map(balance -> {
                log.info("[ERS] Solde dealer: {}", balance);
                return balance;
            })
            .onErrorResume(t -> {
                log.error("[ERS] Erreur consultation solde dealer: {}", t.getMessage());
                return Mono.just(BigDecimal.ZERO);
            });
    }

    // ==================== Appels SOAP (simulés - à remplacer par le vrai client CXF) ====================

    private ErsResult callRequestTopup(String txnId, String clientReference,
                                        String beneficiaryMsisdn, BigDecimal amount, String productId) {
        // TODO: Remplacer par l'appel CXF réel via JaxWsProxyFactoryBean
        // Paramètres SOAP selon la spec ERS :
        // channel = "WebService"
        // clientId = clientId
        // initiatorPrincipalId.type = "RESELLERUSER", .id = initiatorPrincipalId
        // senderPrincipalId.type = "RESELLERID"
        // topupPrincipalId.type = "SUBSCRIBERMSISDN", .id = beneficiaryMsisdn
        // topupAccountSpecifier.accountTypeId = productId != null ? "DATA_BUNDLE" : "AIRTIME"
        // productId = productId (pour bundles)
        // amount.currency = "FCFA", .value = amount
        // clientReference = clientReference
        throw new UnsupportedOperationException("Implémentation CXF à connecter");
    }

    private ErsResult callGetTransactionStatus(String clientReference) {
        // TODO: Remplacer par l'appel CXF réel
        throw new UnsupportedOperationException("Implémentation CXF à connecter");
    }

    private BigDecimal callRequestPrincipalInformation() {
        // TODO: Remplacer par l'appel CXF réel
        throw new UnsupportedOperationException("Implémentation CXF à connecter");
    }

    private boolean isDefinitiveFailureCode(int resultCode) {
        // Codes ERS définitivement échoués (à affiner selon la documentation Expresso)
        return resultCode != 0 && resultCode != 1 && resultCode != 2;
    }

    private void logExpressoCall(String correlationId, String txnId, String clientReference,
                                  String operation, Integer resultCode, String ersReference,
                                  long durationMs, String errorType) {
        expressoCallLogService.indexCall(ExpressoCallDocument.builder()
            .id(correlationId + "-" + operation + "-1")
            .correlationId(correlationId)
            .txnId(txnId)
            .clientReference(clientReference)
            .operation(operation)
            .endpoint(ersUrl)
            .ersResultCode(resultCode)
            .ersReference(ersReference)
            .durationMs((int) durationMs)
            .outcome(errorType != null ? errorType : (resultCode != null && resultCode == 0 ? "SUCCESS" : "BUSINESS_FAILURE"))
            .errorType(errorType)
            .serviceName("expresso-adapter")
            .build())
            .subscribe();
    }

    private String maskMsisdn(String msisdn) {
        if (msisdn == null || msisdn.length() < 8) return "***";
        return msisdn.substring(0, 3) + "****" + msisdn.substring(msisdn.length() - 2);
    }

    record ErsResult(int resultCode, String description, String ersReference) {}
}
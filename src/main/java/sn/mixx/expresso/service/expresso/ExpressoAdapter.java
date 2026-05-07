package sn.mixx.expresso.service.expresso;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import sn.mixx.expresso.domain.elastic.ExpressoCallDocument;
import sn.mixx.expresso.exception.ExpressoBusinessFailureException;
import sn.mixx.expresso.exception.ExpressoTimeoutException;
import sn.mixx.expresso.exception.ExpressoUnavailableException;
import sn.mixx.expresso.service.expresso.soap.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
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
    private final ErsTopupService ersTopupService;

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
            .timeout(Duration.ofMillis(timeoutMs))
            .map(balance -> {
                log.info("[ERS] Solde dealer: {}", balance);
                return balance;
            })
            .onErrorResume(t -> {
                log.error("[ERS] Erreur consultation solde dealer: {}", t.getMessage());
                return Mono.just(BigDecimal.ZERO);
            });
    }

    // ==================== Appels SOAP via CXF ====================

    private ErsResult callRequestTopup(String txnId, String clientReference,
                                        String beneficiaryMsisdn, BigDecimal amount, String productId) {
        RequestTopupRequest req = new RequestTopupRequest();
        req.setChannel("WebService");
        req.setClientId(clientId);
        req.setInitiatorPrincipalId(new PrincipalId("RESELLERUSER", initiatorPrincipalId));
        req.setSenderPrincipalId(new PrincipalId("RESELLERID", null));
        req.setTopupPrincipalId(new PrincipalId("SUBSCRIBERMSISDN", beneficiaryMsisdn));
        AccountSpecifier specifier = new AccountSpecifier();
        specifier.setAccountTypeId(productId != null ? "DATA_BUNDLE" : "AIRTIME");
        req.setTopupAccountSpecifier(specifier);
        req.setProductId(productId);
        req.setAmount(new ErsAmount(amount, "FCFA"));
        req.setClientReference(clientReference);

        RequestTopupResponse resp = ersTopupService.requestTopup(req);
        return new ErsResult(resp.getResultCode(), resp.getResultDescription(), resp.getErsTransactionId());
    }

    private ErsResult callGetTransactionStatus(String clientReference) {
        GetTransactionStatusRequest req = new GetTransactionStatusRequest();
        req.setClientReference(clientReference);

        GetTransactionStatusResponse resp = ersTopupService.getTransactionStatus(req);
        return new ErsResult(resp.getResultCode(), resp.getStatus(), resp.getErsTransactionId());
    }

    private BigDecimal callRequestPrincipalInformation() {
        RequestPrincipalInformationRequest req = new RequestPrincipalInformationRequest();
        req.setPrincipalId(new PrincipalId("RESELLERID", initiatorPrincipalId));

        RequestPrincipalInformationResponse resp = ersTopupService.requestPrincipalInformation(req);
        if (resp.getResultCode() != 0) {
            log.warn("[ERS] requestPrincipalInformation code={}, status={}", resp.getResultCode(), resp.getStatus());
            return BigDecimal.ZERO;
        }
        return resp.getBalance() != null ? resp.getBalance() : BigDecimal.ZERO;
    }

    /**
     * Codes ERS définitivement échoués per spec section 3.5 — pas de retry.
     * 20, 21 : MSISDN invalide/inconnu
     * 30     : Produit indisponible
     * 40     : Plafond ERS (solde dealer insuffisant)
     *
     * Codes transitoires (retryable) : 10, 11, 12 (timeout/busy), 99 (erreur interne ERS)
     */
    private boolean isDefinitiveFailureCode(int resultCode) {
        return resultCode == 20
            || resultCode == 21
            || resultCode == 30
            || resultCode == 40;
    }

    private void logExpressoCall(String correlationId, String txnId, String clientReference,
                                  String operation, Integer resultCode, String ersReference,
                                  long durationMs, String errorType) {
        expressoCallLogService.indexCall(ExpressoCallDocument.builder()
            .id(correlationId + "-" + operation + "-" + Instant.now().toEpochMilli())
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

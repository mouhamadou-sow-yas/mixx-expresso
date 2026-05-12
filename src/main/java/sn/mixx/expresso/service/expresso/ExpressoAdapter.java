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

    private ClientContext buildContext(String clientReference) {
        ClientContext ctx = new ClientContext();
        ctx.setChannel("WebService");
        ctx.setClientId(clientId);
        ctx.setClientReference(clientReference);
        ctx.setClientRequestTimeout(timeoutMs);
        ctx.setPrepareOnly(false);
        ctx.setInitiatorPrincipalId(new PrincipalId("RESELLERUSER", initiatorPrincipalId, "webuser"));
        ctx.setPassword(initiatorPassword);
        return ctx;
    }

    private ErsResult callRequestTopup(String txnId, String clientReference,
                                        String beneficiaryMsisdn, BigDecimal amount, String productId) {
        RequestTopupRequest req = new RequestTopupRequest();
        req.setContext(buildContext(clientReference));
        req.setSenderPrincipalId(new PrincipalId("RESELLERID", initiatorPrincipalId));
        req.setTopupPrincipalId(new PrincipalId("SUBSCRIBERMSISDN", beneficiaryMsisdn));
        req.setSenderAccountSpecifier(new AccountSpecifier("RESELLER"));
        req.setTopupAccountSpecifier(new AccountSpecifier(productId != null ? "DATA_BUNDLE" : "AIRTIME"));
        req.setProductId(productId != null ? productId : "TOPUP");
        req.setAmount(new ErsAmount(amount, "FCFA"));

        RequestTopupResponse resp = ersTopupService.requestTopup(req);
        return new ErsResult(resp.getResultCode(), resp.getResultDescription(), resp.getErsReference());
    }

    private ErsResult callGetTransactionStatus(String clientReference) {
        GetTransactionStatusRequest req = new GetTransactionStatusRequest();
        req.setContext(buildContext(clientReference));
        req.setResellerPrincipalId(new PrincipalId("RESELLERID", initiatorPrincipalId));

        GetTransactionStatusResponse resp = ersTopupService.getTransactionStatus(req);
        // Le statut est dans resultDescription : "ERSTransactionId=...;Status:SUCCESS"
        String parsedStatus = parseStatusFromDescription(resp.getResultDescription());
        return new ErsResult(resp.getResultCode(), parsedStatus, resp.getErsReference());
    }

    private BigDecimal callRequestPrincipalInformation() {
        RequestPrincipalInformationRequest req = new RequestPrincipalInformationRequest();
        req.setContext(buildContext("BALANCE-" + System.currentTimeMillis()));
        req.setPrincipalId(new PrincipalId("RESELLERID", initiatorPrincipalId));

        RequestPrincipalInformationResponse resp = ersTopupService.requestPrincipalInformation(req);
        if (resp.getResultCode() != 0) {
            log.warn("[ERS] requestPrincipalInformation code={}", resp.getResultCode());
            return BigDecimal.ZERO;
        }
        return resp.getBalance() != null ? resp.getBalance() : BigDecimal.ZERO;
    }

    private String parseStatusFromDescription(String description) {
        if (description == null) return null;
        // Format: "ERSTransactionId= xxx;Status:SUCCESS"
        int idx = description.indexOf("Status:");
        if (idx >= 0) {
            return description.substring(idx + 7).trim().split(";")[0];
        }
        return description;
    }

    /**
     * Codes ERS définitivement échoués — pas de retry.
     * Retriables : 0 (SUCCESS), 1 (PENDING_APPROVAL), 93 (SYSTEM_BUSY), 94 (SERVICE_UNAVAILABLE)
     */
    private boolean isDefinitiveFailureCode(int resultCode) {
        return switch (resultCode) {
            // Auth / accès
            case 20, 21, 22, 29 -> true;          // AUTH_FAILED, ACCESS_DENIED, INVALID_PASSWORD, INVALID_INITIATOR
            // Principals invalides / introuvables
            case 30, 31, 32 -> true;               // INVALID_RECEIVER/SENDER/TOPUP_PRINCIPAL_ID
            case 33, 34, 35, 36 -> true;           // INVALID_*_STATE
            case 37, 38, 39, 40 -> true;           // *_PRINCIPAL_NOT_FOUND
            // Produit / compte
            case 41, 42, 43, 44 -> true;           // INVALID_PRODUCT, INVALID_*_ACCOUNT_TYPE
            case 45, 46, 47 -> true;               // *_ACCOUNT_NOT_FOUND
            // Système
            case 90, 91, 92 -> true;               // SYSTEM_ERROR, UNSUPPORTED, LICENSE_REJECTION
            // Retriables : 10 (REJECTED_BUSINESS_LOGIC), 11, 12, 13, 93, 94
            default -> false;
        };
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

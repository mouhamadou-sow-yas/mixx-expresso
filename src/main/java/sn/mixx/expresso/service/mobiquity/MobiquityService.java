package sn.mixx.expresso.service.mobiquity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import sn.mixx.expresso.exception.MobiquityDebitException;

import sn.mixx.expresso.exception.WalletNotFoundException;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class MobiquityService {

    /** WebClient avec base URL https://10.0.37.58:9999 — pour CASHOUT */
    private final WebClient.Builder webClientMobiquity;

    /** WebClient avec base URL https://192.168.41.45:9040 — pour balance et cancel */
    private final WebClient webClientTalend;

    @Value("${mobiquity.timeout:30}")
    private int timeoutSeconds;

    @Value("${mobiquity.merchant_msisdn}")
    private String merchantMsisdn;

    @Value("${mobiquity.merchant_product_id:12}")
    private String merchantProductId;

    @Value("${mobiquity.customer_product_id:71}")
    private String customerProductId;

    @Value("${mobiquity.currency_code:101}")
    private String currencyCode;

    // ==================== Balance ====================

    /**
     * GET /services/mfs/getBalanceNoPIN?msisdn={msisdn}&language=en
     * Host: https://192.168.41.45:9040
     */
    /**
     * Réponse réelle Mobiquity :
     * { "doc": { "param": [ {"name":"resultCode","value":"0"}, {"name":"balance","value":"5000"}, ... ] } }
     */
    public Mono<BigDecimal> getAccountBalance(String msisdn) {
        log.debug("[MOBIQUITY] Vérification solde: {}", maskMsisdn(msisdn));

        return webClientTalend
            .get()
            .uri(uriBuilder -> uriBuilder
                .path("/services/mfs/getBalanceNoPIN")
                .queryParam("msisdn", msisdn)
                .queryParam("language", "en")
                .build())
            .retrieve()
            .bodyToMono(Map.class)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .flatMap(response -> {
                List<Map<String, Object>> params = extractParams(response);
                String resultCode = findParam(params, "resultCode");
                String balance    = findParam(params, "balance");
                String message    = findParam(params, "resultMessage");

                if (!"200".equals(resultCode)) {
                    log.warn("[MOBIQUITY] Balance KO: code={}, msg={}, msisdn={}",
                        resultCode, message, maskMsisdn(msisdn));
                    if ("99971".equals(resultCode) || "Wallet not found".equalsIgnoreCase(message)) {
                        return Mono.error(new WalletNotFoundException(
                            "Aucun compte Mixx pour le numéro " + maskMsisdn(msisdn)));
                    }
                    return Mono.error(new MobiquityDebitException(
                        "Erreur solde Mobiquity (" + resultCode + "): " + message));
                }

                if (balance == null || balance.isBlank()) {
                    log.warn("[MOBIQUITY] Balance vide pour: {}", msisdn);
                    return Mono.error(new MobiquityDebitException("Solde introuvable pour " + msisdn));
                }
                return Mono.just(new BigDecimal(balance.replaceAll("[^0-9.]", "")));
            })
            .retryWhen(Retry.backoff(2, Duration.ofSeconds(2))
                .filter(t -> t instanceof ConnectException || t instanceof WebClientRequestException))
            .doOnError(e -> log.error("[MOBIQUITY] Erreur vérification solde: {}", e.getMessage()));
    }

    // ==================== Débit CASHOUT ====================

    /**
     * POST /jigsaw/serviceRequest/CASHOUT
     * Host: https://10.0.37.58:9999
     *
     * Le PIN transite en TLS uniquement — il est inclus dans le body Mobiquity
     * mais jamais persisté ni loggué.
     */
    public Mono<String> debitAccount(String txnId, String msisdn, BigDecimal amount, String pin) {
        log.info("[MOBIQUITY] Débit CASHOUT: msisdn={}, amount={}, txnId={}", maskMsisdn(msisdn), amount, txnId);

        Map<String, Object> body = buildCashoutBody(txnId, msisdn, amount, pin);
        log.info("[MOBIQUITY] Requete CASHOUT: {}",body);
        return webClientMobiquity.build()
            .post()
            .uri("/jigsaw/serviceRequest/CASHOUT")
            .bodyValue(body)
            .retrieve()
            .bodyToMono(Map.class)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .flatMap(response -> {
                log.info("[MOBIQUITY] Response CASHOUT: {}", response);

                // Tentative 1 : structure JSON plate
                String responseCode = extractString(response, "responseCode", "status");
                String reference    = extractString(response, "transactionId", "reference", "txnId");
                String errorMsg     = extractString(response, "responseMessage", "message", "description");
                if (errorMsg == null) errorMsg = extractErrors(response);

                // Tentative 2 : structure XML doc.param [{"name":"resultCode","value":"..."}]
                if (responseCode == null) {
                    List<Map<String, Object>> params = extractParams(response);
                    responseCode = findParam(params, "resultCode");
                    if (reference == null) reference = findParam(params, "TransactionId");
                    if (errorMsg  == null) errorMsg  = findParam(params, "resultMessage");
                }

                if ("200".equals(responseCode) || "SUCCESS".equalsIgnoreCase(responseCode)
                        || "SUCCEEDED".equalsIgnoreCase(responseCode)) {
                    log.info("[MOBIQUITY] CASHOUT réussi: ref={}, txnId={}", reference, txnId);
                    return Mono.just(reference != null ? reference : txnId);
                }

                String finalMsg = errorMsg != null ? errorMsg : "Code " + responseCode;
                log.warn("[MOBIQUITY] CASHOUT échoué: code={}, msg={}, txnId={}", responseCode, finalMsg, txnId);
                return Mono.error(new MobiquityDebitException(finalMsg));
            })
            .retryWhen(Retry.backoff(2, Duration.ofSeconds(2))
                .filter(t -> t instanceof ConnectException || t instanceof WebClientRequestException))
            .onErrorMap(TimeoutException.class, t ->
                new MobiquityDebitException("Timeout débit Mobiquity pour txnId=" + txnId));
    }

    // ==================== Remboursement ====================

    /**
     * POST /services/mfs/cancelTx
     * Host: https://192.168.41.45:9040
     */
    public Mono<String> refundAccount(String txnId, String msisdn, BigDecimal amount) {
        log.info("[MOBIQUITY] Remboursement: msisdn={}, amount={}, txnId={}", maskMsisdn(msisdn), amount, txnId);

        return webClientTalend
            .post()
            .uri("/services/mfs/cancelTx")
            .bodyValue(Map.of(
                "transactionId", txnId,
                "msisdn", msisdn,
                "amount", amount.toString()
            ))
            .retrieve()
            .bodyToMono(Map.class)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .map(response -> {
                String ref = extractString(response, "transactionId", "reference");
                String refundRef = ref != null ? ref : txnId + "-REFUND";
                log.info("[MOBIQUITY] Remboursement réussi: ref={}, txnId={}", refundRef, txnId);
                return refundRef;
            })
            .retryWhen(Retry.backoff(2, Duration.ofSeconds(2))
                .filter(t -> t instanceof ConnectException || t instanceof WebClientRequestException));
    }

    // ==================== Vérification statut débit ====================

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

    // ==================== Helpers ====================

    private Map<String, Object> buildCashoutBody(String txnId, String msisdn,
                                                   BigDecimal amount, String pin) {
        Map<String, Object> transactor = new HashMap<>();
        transactor.put("idType", "mobileNumber");
        transactor.put("productId", merchantProductId);
        transactor.put("idValue", stripCountryCode(merchantMsisdn));
        transactor.put("userRole", "Channel");

        Map<String, Object> withdrawer = new HashMap<>();
        withdrawer.put("idType", "mobileNumber");
        withdrawer.put("productId", customerProductId);
        withdrawer.put("idValue", stripCountryCode(msisdn));
        withdrawer.put("tpin", pin);
        withdrawer.put("mpin", pin);
        withdrawer.put("pin", pin);
        withdrawer.put("userRole", "Customer");

        Map<String, Object> body = new HashMap<>();
        body.put("serviceCode", "CASHOUT");
        body.put("transactionAmount", amount.toPlainString());
        body.put("initiator", "withdrawer");
        body.put("currency", currencyCode);
        body.put("bearerCode", "USSD");
        body.put("language", "en");
        body.put("transactionMode", "transactionMode");
        body.put("USSDPUSHREQ", "NO");
        body.put("clientReference", txnId);
        body.put("transactor", transactor);
        body.put("withdrawer", withdrawer);
        return body;
    }

    /**
     * Extrait le message d'erreur depuis le champ "errors" de la réponse.
     * Gère : String, Map {"message":"..."}, List [{"message":"..."}]
     */
    @SuppressWarnings("unchecked")
    private String extractErrors(Map<?, ?> response) {
        Object errors = response.get("errors");
        if (errors == null) return null;
        if (errors instanceof String s) return s.isBlank() ? null : s;
        if (errors instanceof Map<?, ?> map) {
            Object msg = map.get("message");
            if (msg == null) msg = map.get("description");
            return msg != null ? msg.toString() : null;
        }
        if (errors instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Map<?, ?> map) {
                Object msg = map.get("message");
                if (msg == null) msg = map.get("description");
                return msg != null ? msg.toString() : null;
            }
            return first.toString();
        }
        return errors.toString();
    }

    /** Retourne la première valeur non nulle parmi les champs donnés. */
    @SuppressWarnings("unchecked")
    private String extractString(Map<?, ?> response, String... fields) {
        for (String field : fields) {
            Object val = response.get(field);
            if (val != null) return val.toString();
        }
        return null;
    }

    /** Extrait le tableau param depuis la structure { "doc": { "param": [...] } } */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractParams(Map<?, ?> response) {
        try {
            Object doc = response.get("doc");
            if (doc instanceof Map<?, ?> docMap) {
                Object param = docMap.get("param");
                if (param instanceof List<?> list) {
                    return (List<Map<String, Object>>) list;
                }
            }
        } catch (Exception ignored) {}
        return List.of();
    }

    /** Trouve la valeur d'un param par son name — convertit en String quelle que soit le type JSON. */
    private String findParam(List<Map<String, Object>> params, String name) {
        return params.stream()
            .filter(p -> name.equals(p.get("name")))
            .map(p -> p.get("value"))
            .filter(v -> v != null)
            .map(Object::toString)
            .findFirst()
            .orElse(null);
    }

    private String stripCountryCode(String msisdn) {
        if (msisdn == null) return null;
        return msisdn.startsWith("221") ? msisdn.substring(3) : msisdn;
    }

    private String maskMsisdn(String msisdn) {
        if (msisdn == null || msisdn.length() < 8) return "***";
        return msisdn.substring(0, 3) + "****" + msisdn.substring(msisdn.length() - 2);
    }
}
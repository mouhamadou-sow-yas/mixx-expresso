package sn.mixx.expresso.web.rest.errors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;
import sn.mixx.expresso.exception.*;
import sn.mixx.expresso.security.AccountLockedException;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ExceptionTranslator {

    @ExceptionHandler(AccountLockedException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleAccountLocked(AccountLockedException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", 429);
        body.put("title", "Too Many Requests");
        body.put("detail", ex.getMessage());
        body.put("remainingMinutes", ex.getRemainingMinutes());
        return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(body));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleBadCredentials(BadCredentialsException ex) {
        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(errorBody(401, "Unauthorized", ex.getMessage())));
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleUsernameNotFound(UsernameNotFoundException ex) {
        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(errorBody(401, "Unauthorized", "Utilisateur introuvable")));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleAccessDenied(AccessDeniedException ex) {
        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(errorBody(403, "Forbidden", ex.getMessage())));
    }

    @ExceptionHandler(ExpressoTimeoutException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleExpressoTimeout(ExpressoTimeoutException ex) {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(errorBody(503, "Service Unavailable", ex.getMessage())));
    }

    @ExceptionHandler(ExpressoBusinessFailureException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleExpressoBusinessFailure(ExpressoBusinessFailureException ex) {
        Map<String, Object> body = errorBody(422, "Unprocessable Entity", ex.getMessage());
        body.put("ersResultCode", ex.getResultCode());
        return Mono.just(ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body));
    }

    @ExceptionHandler(ExpressoUnavailableException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleExpressoUnavailable(ExpressoUnavailableException ex) {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(errorBody(503, "Service Unavailable", ex.getMessage())));
    }

    @ExceptionHandler(WalletNotFoundException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleWalletNotFound(WalletNotFoundException ex) {
        return Mono.just(ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(txnErrorBody("TXN_WALLET_NOT_FOUND", ex.getMessage())));
    }

    @ExceptionHandler(MobiquityDebitException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleMobiquityDebit(MobiquityDebitException ex) {
        return Mono.just(ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(txnErrorBody("TXN_DEBIT_FAILED", ex.getMessage())));
    }

    @ExceptionHandler(MobiquityRefundException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleMobiquityRefund(MobiquityRefundException ex) {
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(errorBody(500, "Refund Error", ex.getMessage())));
    }

    @ExceptionHandler(AntiFraudException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleAntiFraud(AntiFraudException ex) {
        return switch (ex.getReason()) {
            case "DUPLICATE" -> Mono.just(ResponseEntity.status(HttpStatus.CONFLICT)
                .body(txnErrorBody("TXN_DUPLICATE", ex.getMessage())));
            case "DAILY_LIMIT_EXCEEDED" -> Mono.just(ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(txnErrorBody("TXN_DAILY_LIMIT_EXCEEDED", ex.getMessage())));
            case "MONTHLY_LIMIT_EXCEEDED" -> Mono.just(ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(txnErrorBody("TXN_MONTHLY_LIMIT_EXCEEDED", ex.getMessage())));
            case "RATE_LIMIT_EXCEEDED" -> Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(txnErrorBody("TXN_ANTI_FRAUD_BLOCK", ex.getMessage())));
            default -> Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(txnErrorBody("TXN_ANTI_FRAUD_BLOCK", ex.getMessage())));
        };
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleInsufficientBalance(InsufficientBalanceException ex) {
        return Mono.just(ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(txnErrorBody("TXN_INSUFFICIENT_BALANCE", ex.getMessage())));
    }

    @ExceptionHandler(BundleNotFoundException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleBundleNotFound(BundleNotFoundException ex) {
        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(errorBody(404, "Not Found", ex.getMessage())));
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleTransactionNotFound(TransactionNotFoundException ex) {
        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(errorBody(404, "Not Found", ex.getMessage())));
    }

    @ExceptionHandler(InvalidTransactionStateException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleInvalidState(InvalidTransactionStateException ex) {
        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT)
            .body(errorBody(409, "Conflict", ex.getMessage())));
    }

    @ExceptionHandler(PartnerNotFoundException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handlePartnerNotFound(PartnerNotFoundException ex) {
        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(errorBody(404, "Not Found", ex.getMessage())));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleDataIntegrity(DataIntegrityViolationException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage().toUpperCase() : "";
        if (msg.contains("UNIQUE") || msg.contains("DUPLICATE")) {
            return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT)
                .body(txnErrorBody("TXN_DUPLICATE", "Transaction déjà soumise avec ce correlationId")));
        }
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(txnErrorBody("INTERNAL_ERROR", "Erreur base de données")));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleValidation(WebExchangeBindException ex) {
        Map<String, Object> body = errorBody(400, "Bad Request", "Erreur de validation");
        body.put("fieldErrors", ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> Map.of("field", fe.getField(), "message", fe.getDefaultMessage() != null ? fe.getDefaultMessage() : ""))
            .collect(Collectors.toList()));
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body));
    }

    private Map<String, Object> errorBody(int status, String title, String detail) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status);
        body.put("title", title);
        body.put("detail", detail);
        return body;
    }

    private Map<String, Object> txnErrorBody(String code, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "ERROR");
        body.put("error", Map.of("code", code, "message", message));
        return body;
    }
}
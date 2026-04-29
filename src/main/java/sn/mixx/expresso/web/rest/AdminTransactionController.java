package sn.mixx.expresso.web.rest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import sn.mixx.expresso.domain.Transaction;
import sn.mixx.expresso.domain.TransactionStatusHistory;
import sn.mixx.expresso.security.SecurityUtils;
import sn.mixx.expresso.service.backoffice.BackOfficeService;

import java.time.Instant;

@Slf4j
@RestController
@RequestMapping("/api/admin/transactions")
@RequiredArgsConstructor
public class AdminTransactionController {

    private final BackOfficeService backOfficeService;

    @GetMapping
    public Flux<Transaction> searchTransactions(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String msisdn,
        @RequestParam(required = false) String txnId,
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to) {

        Instant fromInstant = from != null ? Instant.parse(from) : null;
        Instant toInstant = to != null ? Instant.parse(to) : null;

        return backOfficeService.searchTransactions(status, msisdn, txnId, fromInstant, toInstant);
    }

    @GetMapping("/{txnId}")
    public Mono<ResponseEntity<Transaction>> getTransaction(@PathVariable String txnId) {
        return backOfficeService.getTransactionDetail(txnId).map(ResponseEntity::ok);
    }

    @GetMapping("/{txnId}/history")
    public Flux<TransactionStatusHistory> getStatusHistory(@PathVariable String txnId) {
        return backOfficeService.getStatusHistory(txnId);
    }

    @PostMapping("/{txnId}/retry")
    public Mono<ResponseEntity<Transaction>> manualRetry(@PathVariable String txnId) {
        return SecurityUtils.getCurrentUserLogin()
            .flatMap(login -> backOfficeService.manualRetry(txnId, login))
            .map(ResponseEntity::ok);
    }

    @PostMapping("/{txnId}/cancel")
    public Mono<ResponseEntity<Transaction>> cancelTransaction(@PathVariable String txnId) {
        return SecurityUtils.getCurrentUserLogin()
            .flatMap(login -> backOfficeService.cancelTransaction(txnId, login))
            .map(ResponseEntity::ok);
    }

    @PostMapping("/{txnId}/refund")
    public Mono<ResponseEntity<Transaction>> manualRefund(@PathVariable String txnId) {
        log.info("[BO-API] POST /admin/transactions/{}/refund", txnId);
        return SecurityUtils.getCurrentUserLogin()
            .flatMap(login -> backOfficeService.manualRefund(txnId, login))
            .map(ResponseEntity::ok);
    }
}
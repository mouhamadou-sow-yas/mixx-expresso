package sn.mixx.expresso.web.rest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import sn.mixx.expresso.service.dto.request.AirtimePurchaseRequest;
import sn.mixx.expresso.service.dto.request.BundlePurchaseRequest;
import sn.mixx.expresso.service.dto.response.ApiResponse;
import sn.mixx.expresso.service.dto.response.TransactionResponse;
import sn.mixx.expresso.service.transaction.TransactionService;

@Slf4j
@RestController
@RequestMapping("/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/airtime")
    public Mono<ResponseEntity<ApiResponse<TransactionResponse>>> purchaseAirtime(
        @Valid @RequestBody AirtimePurchaseRequest request) {
        log.info("[API] POST /v1/transactions/airtime: correlationId={}", request.getCorrelationId());
        return transactionService.createAirtimeTransaction(request)
            .map(data -> ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(data)));
    }

    @PostMapping("/bundle")
    public Mono<ResponseEntity<ApiResponse<TransactionResponse>>> purchaseBundle(
        @Valid @RequestBody BundlePurchaseRequest request) {
        log.info("[API] POST /v1/transactions/bundle: correlationId={}", request.getCorrelationId());
        return transactionService.createBundleTransaction(request)
            .map(data -> ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(data)));
    }

    @GetMapping("/{txnId}")
    public Mono<ResponseEntity<ApiResponse<TransactionResponse>>> getTransaction(
        @PathVariable String txnId) {
        return transactionService.getTransaction(txnId)
            .map(data -> ResponseEntity.ok(ApiResponse.success(data)));
    }
}
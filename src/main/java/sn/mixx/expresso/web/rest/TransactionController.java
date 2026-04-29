package sn.mixx.expresso.web.rest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import sn.mixx.expresso.service.dto.request.AirtimePurchaseRequest;
import sn.mixx.expresso.service.dto.request.BundlePurchaseRequest;
import sn.mixx.expresso.service.dto.response.TransactionResponse;
import sn.mixx.expresso.service.transaction.TransactionService;

@Slf4j
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/airtime")
    public Mono<ResponseEntity<TransactionResponse>> purchaseAirtime(
        @Valid @RequestBody AirtimePurchaseRequest request) {
        log.info("[API] POST /transactions/airtime: clientRef={}", request.getClientReference());
        return transactionService.createAirtimeTransaction(request)
            .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
    }

    @PostMapping("/bundle")
    public Mono<ResponseEntity<TransactionResponse>> purchaseBundle(
        @Valid @RequestBody BundlePurchaseRequest request) {
        log.info("[API] POST /transactions/bundle: clientRef={}", request.getClientReference());
        return transactionService.createBundleTransaction(request)
            .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
    }

    @GetMapping("/{txnId}")
    public Mono<ResponseEntity<TransactionResponse>> getTransaction(@PathVariable String txnId) {
        return transactionService.getTransaction(txnId)
            .map(ResponseEntity::ok);
    }
}
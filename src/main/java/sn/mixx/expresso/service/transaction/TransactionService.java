package sn.mixx.expresso.service.transaction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import sn.mixx.expresso.domain.Transaction;
import sn.mixx.expresso.domain.TransactionStatusHistory;
import sn.mixx.expresso.exception.*;
import sn.mixx.expresso.repository.BundleProductRepository;
import sn.mixx.expresso.repository.TransactionRepository;
import sn.mixx.expresso.repository.TransactionStatusHistoryRepository;
import sn.mixx.expresso.service.dto.request.AirtimePurchaseRequest;
import sn.mixx.expresso.service.dto.request.BundlePurchaseRequest;
import sn.mixx.expresso.service.dto.response.TransactionResponse;
import sn.mixx.expresso.service.elastic.ElasticPendingTransactionService;
import sn.mixx.expresso.service.expresso.ExpressoAdapter;
import sn.mixx.expresso.service.mobiquity.MobiquityService;
import sn.mixx.expresso.service.notification.NotificationService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Orchestrateur central du cycle de vie des transactions.
 *
 * Flow airtime/bundle :
 * 1. Anti-fraude (doublon, plafonds, rate limit)
 * 2. Vérification solde Mobiquity
 * 3. Débit Mobiquity → debit_confirmed = true → status = PENDING
 * 4. requestTopup Expresso
 *    - SUCCESS → COMPLETED + notification
 *    - Timeout/Indisponible → reste PENDING (retry scheduler prend le relai)
 *    - Échec définitif → FAILED + alerte BO (pas de remboursement auto)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionStatusHistoryRepository statusHistoryRepository;
    private final BundleProductRepository bundleProductRepository;
    private final MobiquityService mobiquityService;
    private final ExpressoAdapter expressoAdapter;
    private final AntiFraudService antiFraudService;
    private final NotificationService notificationService;
    private final ElasticPendingTransactionService elasticPendingService;

    @Value("${elasticsearch.default.retry.count:5}")
    private int maxRetryCount;

    @Value("${mobiquity.pending_cutoff_minutes:2}")
    private int pendingCutoffMinutes;

    public Mono<TransactionResponse> createAirtimeTransaction(AirtimePurchaseRequest request) {
        log.info("[TXN] Création airtime: msisdn={}, amount={}, clientRef={}",
            maskMsisdn(request.getClientMsisdn()), request.getAmount(), request.getClientReference());

        String txnId = UUID.randomUUID().toString();
        String correlationId = request.getCorrelationId() != null ? request.getCorrelationId() : UUID.randomUUID().toString();

        return antiFraudService.checkAntiDuplicate(request.getClientReference())
            .then(antiFraudService.checkRateLimit(request.getClientMsisdn()))
            .then(antiFraudService.checkDailyLimit(request.getClientMsisdn(), request.getAmount()))
            .then(antiFraudService.checkMonthlyLimit(request.getClientMsisdn(), request.getAmount()))
            .then(createAndSaveTransaction(txnId, request.getClientReference(), "AIRTIME",
                request.getChannel(), request.getClientMsisdn(), request.getBeneficiaryMsisdn(),
                request.getAmount(), null, null, correlationId))
            .flatMap(transaction -> processTransaction(transaction, correlationId))
            .map(this::toResponse);
    }

    public Mono<TransactionResponse> createBundleTransaction(BundlePurchaseRequest request) {
        log.info("[TXN] Création bundle: msisdn={}, product={}, clientRef={}",
            maskMsisdn(request.getClientMsisdn()), request.getErsProductId(), request.getClientReference());

        String txnId = UUID.randomUUID().toString();
        String correlationId = request.getCorrelationId() != null ? request.getCorrelationId() : UUID.randomUUID().toString();

        return bundleProductRepository.findByErsProductId(request.getErsProductId())
            .switchIfEmpty(Mono.error(new BundleNotFoundException("Bundle introuvable: " + request.getErsProductId())))
            .filter(product -> Boolean.TRUE.equals(product.getIsActive()))
            .switchIfEmpty(Mono.error(new BundleNotFoundException("Bundle inactif: " + request.getErsProductId())))
            .flatMap(product ->
                antiFraudService.checkAntiDuplicate(request.getClientReference())
                    .then(antiFraudService.checkRateLimit(request.getClientMsisdn()))
                    .then(antiFraudService.checkDailyLimit(request.getClientMsisdn(), product.getPrice()))
                    .then(antiFraudService.checkMonthlyLimit(request.getClientMsisdn(), product.getPrice()))
                    .then(createAndSaveTransaction(txnId, request.getClientReference(), "BUNDLE",
                        request.getChannel(), request.getClientMsisdn(), request.getBeneficiaryMsisdn(),
                        product.getPrice(), product.getErsProductId(), product.getCategory(), correlationId))
            )
            .flatMap(transaction -> processTransaction(transaction, correlationId))
            .map(this::toResponse);
    }

    private Mono<Transaction> processTransaction(Transaction transaction, String correlationId) {
        return mobiquityService.getAccountBalance(transaction.getClientMsisdn())
            .flatMap(balance -> {
                if (balance.compareTo(transaction.getAmount().add(transaction.getFees() != null ? transaction.getFees() : BigDecimal.ZERO)) < 0) {
                    return Mono.<Transaction>error(new InsufficientBalanceException(
                        "Solde insuffisant pour msisdn=" + maskMsisdn(transaction.getClientMsisdn())));
                }
                return Mono.just(balance);
            })
            .then(mobiquityService.debitAccount(transaction.getTxnId(), transaction.getClientMsisdn(), transaction.getAmount()))
            .flatMap(debitRef -> {
                transaction.setMobiquityDebitRef(debitRef);
                transaction.setDebitConfirmed(true);
                transaction.setStatus("PENDING");
                transaction.setUpdatedAt(Instant.now());
                return transactionRepository.save(transaction)
                    .then(saveStatusHistory(transaction.getId(), "CREATED", "PENDING", "Débit Mobiquity confirmé", "SYSTEM"));
            })
            .then(Mono.defer(() -> expressoAdapter.requestTopup(
                transaction.getTxnId(),
                transaction.getClientReference(),
                transaction.getBeneficiaryMsisdn(),
                transaction.getAmount(),
                transaction.getProductId(),
                correlationId
            )))
            .flatMap(ersReference -> {
                transaction.setErsReference(ersReference);
                transaction.setErsResultCode(0);
                transaction.setStatus("COMPLETED");
                transaction.setCompletedAt(Instant.now());
                transaction.setUpdatedAt(Instant.now());
                return transactionRepository.save(transaction)
                    .then(saveStatusHistory(transaction.getId(), "PENDING", "COMPLETED", "Recharge Expresso confirmée", "SYSTEM"))
                    .then(notificationService.sendSuccessNotification(transaction))
                    .thenReturn(transaction);
            })
            .onErrorResume(ExpressoTimeoutException.class, ex -> {
                log.warn("[TXN] Timeout Expresso → PENDING pour retry: txnId={}", transaction.getTxnId());
                transaction.setStatus("PENDING");
                transaction.setFailureReason("Timeout ERS - en attente de confirmation");
                transaction.setNextRetryAt(Instant.now().plus(pendingCutoffMinutes, ChronoUnit.MINUTES));
                transaction.setUpdatedAt(Instant.now());
                return transactionRepository.save(transaction)
                    .then(saveStatusHistory(transaction.getId(), "PENDING", "PENDING", "Timeout Expresso - retry planifié", "SYSTEM"))
                    .then(elasticPendingService.indexPendingTransaction(transaction))
                    .thenReturn(transaction);
            })
            .onErrorResume(ExpressoUnavailableException.class, ex -> {
                log.warn("[TXN] Expresso indisponible → PENDING: txnId={}", transaction.getTxnId());
                transaction.setStatus("PENDING");
                transaction.setFailureReason("ERS indisponible - retry planifié");
                transaction.setNextRetryAt(Instant.now().plus(pendingCutoffMinutes, ChronoUnit.MINUTES));
                transaction.setUpdatedAt(Instant.now());
                return transactionRepository.save(transaction)
                    .then(saveStatusHistory(transaction.getId(), "PENDING", "PENDING", "ERS indisponible - retry planifié", "SYSTEM"))
                    .then(elasticPendingService.indexPendingTransaction(transaction))
                    .thenReturn(transaction);
            })
            .onErrorResume(ExpressoBusinessFailureException.class, ex -> {
                log.warn("[TXN] Échec définitif Expresso → FAILED (alerte BO): txnId={}, code={}",
                    transaction.getTxnId(), ex.getResultCode());
                transaction.setStatus("FAILED");
                transaction.setErsResultCode(ex.getResultCode());
                transaction.setFailureReason("Échec ERS: " + ex.getMessage());
                transaction.setUpdatedAt(Instant.now());
                return transactionRepository.save(transaction)
                    .then(saveStatusHistory(transaction.getId(), "PENDING", "FAILED",
                        "Échec définitif ERS code=" + ex.getResultCode(), "SYSTEM"))
                    .then(notificationService.sendFailureNotification(transaction))
                    .thenReturn(transaction);
            });
    }

    public Mono<TransactionResponse> getTransaction(String txnId) {
        return transactionRepository.findByTxnId(txnId)
            .switchIfEmpty(Mono.error(new TransactionNotFoundException("Transaction introuvable: " + txnId)))
            .map(this::toResponse);
    }

    public Flux<TransactionResponse> getClientHistory(String msisdn) {
        return transactionRepository.findByClientMsisdnOrderByCreatedAtDesc(msisdn)
            .map(this::toResponse);
    }

    private Mono<Transaction> createAndSaveTransaction(
        String txnId, String clientReference, String type, String channel,
        String clientMsisdn, String beneficiaryMsisdn, BigDecimal amount,
        String productId, String productCategory, String correlationId) {

        Transaction transaction = Transaction.builder()
            .txnId(txnId)
            .clientReference(clientReference)
            .type(type)
            .status("CREATED")
            .channel(channel)
            .clientMsisdn(clientMsisdn)
            .beneficiaryMsisdn(beneficiaryMsisdn)
            .amount(amount)
            .fees(BigDecimal.ZERO)
            .currency("XOF")
            .productId(productId)
            .productCategory(productCategory)
            .retryCount(0)
            .debitConfirmed(false)
            .correlationId(correlationId)
            .createdAt(Instant.now())
            .build();

        return transactionRepository.save(transaction)
            .flatMap(saved -> saveStatusHistory(saved.getId(), null, "CREATED", "Transaction créée", "SYSTEM")
                .thenReturn(saved));
    }

    private Mono<Void> saveStatusHistory(Long txnId, String oldStatus, String newStatus, String reason, String createdBy) {
        if (txnId == null) return Mono.empty();
        return statusHistoryRepository.save(TransactionStatusHistory.builder()
            .transactionId(txnId)
            .oldStatus(oldStatus)
            .newStatus(newStatus)
            .reason(reason)
            .createdBy(createdBy)
            .build())
            .then();
    }

    private TransactionResponse toResponse(Transaction t) {
        return TransactionResponse.builder()
            .txnId(t.getTxnId())
            .clientReference(t.getClientReference())
            .type(t.getType())
            .status(t.getStatus())
            .channel(t.getChannel())
            .clientMsisdn(t.getClientMsisdn())
            .beneficiaryMsisdn(t.getBeneficiaryMsisdn())
            .amount(t.getAmount())
            .fees(t.getFees())
            .currency(t.getCurrency())
            .productId(t.getProductId())
            .productCategory(t.getProductCategory())
            .ersReference(t.getErsReference())
            .failureReason(t.getFailureReason())
            .correlationId(t.getCorrelationId())
            .createdAt(t.getCreatedAt())
            .updatedAt(t.getUpdatedAt())
            .completedAt(t.getCompletedAt())
            .build();
    }

    private String maskMsisdn(String msisdn) {
        if (msisdn == null || msisdn.length() < 8) return "***";
        return msisdn.substring(0, 3) + "****" + msisdn.substring(msisdn.length() - 2);
    }
}
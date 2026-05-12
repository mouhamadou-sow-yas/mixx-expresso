package sn.mixx.expresso.service.transaction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import sn.mixx.expresso.domain.BundleProduct;
import sn.mixx.expresso.domain.Transaction;
import sn.mixx.expresso.domain.TransactionStatusHistory;
import sn.mixx.expresso.exception.*;
import sn.mixx.expresso.repository.BundleProductRepository;
import sn.mixx.expresso.repository.TransactionRepository;
import sn.mixx.expresso.repository.TransactionStatusHistoryRepository;
import sn.mixx.expresso.service.dto.request.AirtimePurchaseRequest;
import sn.mixx.expresso.service.dto.request.BundlePurchaseRequest;
import sn.mixx.expresso.service.dto.response.PagedResponse;
import sn.mixx.expresso.service.dto.response.TransactionResponse;
import sn.mixx.expresso.service.audit.AuditService;
import sn.mixx.expresso.service.elastic.ElasticPendingTransactionService;
import sn.mixx.expresso.service.elastic.ElasticTransactionTraceService;
import sn.mixx.expresso.service.expresso.ExpressoAdapter;
import sn.mixx.expresso.service.mobiquity.MobiquityService;
import sn.mixx.expresso.service.notification.NotificationService;

import sn.mixx.expresso.domain.enums.AuditAction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    private final ElasticTransactionTraceService elasticTraceService;
    private final AuditService auditService;

    @Value("${elasticsearch.default.retry.count:5}")
    private int maxRetryCount;

    @Value("${mobiquity.pending_cutoff_minutes:2}")
    private int pendingCutoffMinutes;

    public Mono<TransactionResponse> createAirtimeTransaction(AirtimePurchaseRequest request) {
        String clientMsisdn = normalizeMsisdn(request.getSender().getMsisdn());
        String beneficiaryMsisdn = normalizeMsisdn(request.getReceiver().getMsisdn());
        BigDecimal amount = request.getAmount().getValue();
        String correlationId = request.getCorrelationId();
        String pin = request.getSender().getPin();

        log.info("[TXN] Création airtime: msisdn={}, amount={}, correlationId={}",
            maskMsisdn(clientMsisdn), amount, correlationId);

        String txnId = UUID.randomUUID().toString();

        return antiFraudService.checkAntiDuplicate(correlationId)
            .then(antiFraudService.checkRateLimit(clientMsisdn))
            .then(antiFraudService.checkDailyLimit(clientMsisdn, amount))
            .then(antiFraudService.checkMonthlyLimit(clientMsisdn, amount))
            .then(createAndSaveTransaction(txnId, correlationId, "AIRTIME",
                    "APP", clientMsisdn, beneficiaryMsisdn, amount, null, null, correlationId))
            .flatMap(transaction -> processTransaction(transaction, pin, correlationId))
            .map(t -> toResponse(t, null));
    }

    public Mono<TransactionResponse> createBundleTransaction(BundlePurchaseRequest request) {
        String clientMsisdn = normalizeMsisdn(request.getSender().getMsisdn());
        String beneficiaryMsisdn = normalizeMsisdn(request.getReceiver().getMsisdn());
        String bundleCode = request.getBundle().getBundleCode();
        String correlationId = request.getCorrelationId();
        String pin = request.getSender().getPin();

        log.info("[TXN] Création bundle: msisdn={}, bundle={}, correlationId={}",
            maskMsisdn(clientMsisdn), bundleCode, correlationId);

        String txnId = UUID.randomUUID().toString();

        return bundleProductRepository.findByErsProductId(bundleCode)
            .switchIfEmpty(Mono.error(new BundleNotFoundException("Bundle introuvable: " + bundleCode)))
            .filter(product -> Boolean.TRUE.equals(product.getIsActive()))
            .switchIfEmpty(Mono.error(new BundleNotFoundException("Bundle inactif: " + bundleCode)))
            .flatMap(product ->
                antiFraudService.checkAntiDuplicate(correlationId)
                    .then(antiFraudService.checkRateLimit(clientMsisdn))
                    .then(antiFraudService.checkDailyLimit(clientMsisdn, product.getPrice()))
                    .then(antiFraudService.checkMonthlyLimit(clientMsisdn, product.getPrice()))
                    .then(createAndSaveTransaction(txnId, correlationId, "BUNDLE",
                            "APP", clientMsisdn, beneficiaryMsisdn, product.getPrice(),
                        product.getErsProductId(), product.getCategory(), correlationId))
                    .flatMap(transaction -> processTransaction(transaction, pin, correlationId))
                    .map(t -> toResponse(t, product))
            );
    }

    private Mono<Transaction> processTransaction(Transaction transaction, String pin, String correlationId) {
        return mobiquityService.getAccountBalance(transaction.getClientMsisdn())
            .flatMap(balance -> {
                BigDecimal fees = transaction.getFees() != null ? transaction.getFees() : BigDecimal.ZERO;
                BigDecimal required = transaction.getAmount().add(fees);
                log.info("[TXN] Vérification solde: disponible={} XOF, requis={} XOF, msisdn={}",
                    balance, required, maskMsisdn(transaction.getClientMsisdn()));
                if (balance.compareTo(required) < 0) {
                    log.warn("[TXN] Solde insuffisant: disponible={} < requis={}", balance, required);
                    return Mono.<BigDecimal>error(new InsufficientBalanceException(
                        String.format("Solde insuffisant: disponible %s XOF, requis %s XOF",
                            balance.setScale(0, java.math.RoundingMode.FLOOR),
                            required.setScale(0, java.math.RoundingMode.CEILING))));
                }
                return Mono.just(balance);
            })
            .flatMap(balance -> mobiquityService.debitAccount(transaction.getTxnId(),
                transaction.getClientMsisdn(), transaction.getAmount(), pin))
            .flatMap(debitRef -> {
                transaction.setMobiquityDebitRef(debitRef);
                transaction.setDebitConfirmed(true);
                transaction.setStatus("PENDING");
                transaction.setUpdatedAt(Instant.now());
                auditService.log(
                    AuditAction.DEBIT, "TRANSACTION", transaction.getTxnId(), "SYSTEM",
                    Map.of("msisdn", maskMsisdn(transaction.getClientMsisdn()), "amount", transaction.getAmount()),
                    Map.of("debitRef", debitRef, "debitConfirmed", true),
                    "SUCCESS", correlationId);
                return transactionRepository.save(transaction)
                    .then(saveStatusHistory(transaction.getId(), "CREATED", "PENDING",
                        "Débit Mobiquity confirmé", "SYSTEM"));
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
                auditService.log(
                    AuditAction.TOPUP, "TRANSACTION", transaction.getTxnId(), "SYSTEM",
                    Map.of("beneficiaryMsisdn", maskMsisdn(transaction.getBeneficiaryMsisdn()),
                           "amount", transaction.getAmount(), "productId",
                           transaction.getProductId() != null ? transaction.getProductId() : "AIRTIME"),
                    Map.of("ersReference", ersReference, "ersResultCode", 0, "status", "COMPLETED"),
                    "SUCCESS", correlationId);
                elasticTraceService.index(transaction);
                return transactionRepository.save(transaction)
                    .then(saveStatusHistory(transaction.getId(), "PENDING", "COMPLETED",
                        "Recharge Expresso confirmée", "SYSTEM"))
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
                    .then(saveStatusHistory(transaction.getId(), "PENDING", "PENDING",
                        "Timeout Expresso - retry planifié", "SYSTEM"))
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
                    .then(saveStatusHistory(transaction.getId(), "PENDING", "PENDING",
                        "ERS indisponible - retry planifié", "SYSTEM"))
                    .then(elasticPendingService.indexPendingTransaction(transaction))
                    .thenReturn(transaction);
            })
            .onErrorResume(ExpressoBusinessFailureException.class, ex -> {
                log.warn("[TXN] Échec définitif Expresso → FAILED: txnId={}, code={}",
                    transaction.getTxnId(), ex.getResultCode());
                transaction.setStatus("FAILED");
                transaction.setErsResultCode(ex.getResultCode());
                transaction.setFailureReason("Échec ERS: " + ex.getMessage());
                transaction.setUpdatedAt(Instant.now());
                auditService.log(
                    AuditAction.TOPUP, "TRANSACTION", transaction.getTxnId(), "SYSTEM",
                    Map.of("beneficiaryMsisdn", maskMsisdn(transaction.getBeneficiaryMsisdn()),
                           "amount", transaction.getAmount()),
                    Map.of("ersResultCode", ex.getResultCode(), "error", ex.getMessage(), "status", "FAILED"),
                    "FAILURE", correlationId);
                elasticTraceService.index(transaction);
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
            .map(t -> toResponse(t, null));
    }

    public Mono<PagedResponse<TransactionResponse>> getClientHistory(
        String msisdn, String status, String type,
        Instant from, Instant to, int page, int size) {

        int offset = (page - 1) * size;

        Mono<List<TransactionResponse>> items = transactionRepository
            .findByClientMsisdnFiltered(msisdn, status, type, from, to, offset, size)
            .map(t -> toResponse(t, null))
            .collectList();

        Mono<Long> total = transactionRepository
            .countByClientMsisdnFiltered(msisdn, status, type, from, to);

        return Mono.zip(items, total)
            .map(tuple -> PagedResponse.<TransactionResponse>builder()
                .items(tuple.getT1())
                .page(page)
                .size(size)
                .total(tuple.getT2())
                .build());
    }

    // ==================== Helpers ====================

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
            .flatMap(saved -> {
                auditService.log(
                    AuditAction.CREATE_TXN, "TRANSACTION", saved.getTxnId(), "SYSTEM",
                    Map.of("type", type, "clientMsisdn", maskMsisdn(clientMsisdn),
                           "beneficiaryMsisdn", maskMsisdn(beneficiaryMsisdn),
                           "amount", amount, "channel", channel),
                    Map.of("txnId", saved.getTxnId(), "status", "CREATED"),
                    "SUCCESS", correlationId);
                return saveStatusHistory(saved.getId(), null, "CREATED", "Transaction créée", "SYSTEM")
                    .thenReturn(saved);
            });
    }

    private Mono<Void> saveStatusHistory(Long txnId, String oldStatus, String newStatus,
                                          String reason, String createdBy) {
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

    private TransactionResponse toResponse(Transaction t, BundleProduct product) {
        TransactionResponse.TransactionResponseBuilder builder = TransactionResponse.builder()
            .txnId(t.getTxnId())
            .type(t.getType())
            .status(t.getStatus())
            .amount(t.getAmount())
            .currency(t.getCurrency())
            .beneficiaryMsisdn(t.getBeneficiaryMsisdn())
            .ersTransactionId(t.getErsReference())
            .retryCount(t.getRetryCount())
            .createdAt(t.getCreatedAt())
            .completedAt(t.getCompletedAt());

        if ("PENDING".equals(t.getStatus()) || "CREATED".equals(t.getStatus())) {
            builder.estimatedCompletion(Instant.now().plusSeconds(15));
        }

        if (product != null) {
            builder.bundleCode(product.getErsProductId())
                .validityDays(product.getValidityDays());
        }

        return builder.build();
    }

    private String normalizeMsisdn(String msisdn) {
        if (msisdn == null) return null;
        String digits = msisdn.replaceAll("\\s+", "");
        if (digits.length() == 9) return "221" + digits;
        return digits;
    }

    private String maskMsisdn(String msisdn) {
        if (msisdn == null || msisdn.length() < 8) return "***";
        return msisdn.substring(0, 3) + "****" + msisdn.substring(msisdn.length() - 2);
    }
}
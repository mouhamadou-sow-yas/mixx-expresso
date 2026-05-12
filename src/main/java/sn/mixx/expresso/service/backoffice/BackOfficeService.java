package sn.mixx.expresso.service.backoffice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import sn.mixx.expresso.domain.Transaction;
import sn.mixx.expresso.domain.TransactionStatusHistory;
import sn.mixx.expresso.exception.InvalidTransactionStateException;
import sn.mixx.expresso.exception.MobiquityRefundException;
import sn.mixx.expresso.exception.TransactionNotFoundException;
import sn.mixx.expresso.repository.TransactionRepository;
import sn.mixx.expresso.repository.TransactionStatusHistoryRepository;
import sn.mixx.expresso.domain.enums.AuditAction;
import sn.mixx.expresso.service.audit.AuditService;
import sn.mixx.expresso.service.elastic.ElasticPendingTransactionService;
import sn.mixx.expresso.service.expresso.ExpressoAdapter;
import sn.mixx.expresso.service.mobiquity.MobiquityService;
import sn.mixx.expresso.service.notification.NotificationService;

import java.time.Instant;
import java.util.Map;

/**
 * Service back-office : recherche, relance manuelle, annulation, remboursement manuel.
 * Le remboursement est UNIQUEMENT déclenché ici (jamais automatiquement).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackOfficeService {

    private final TransactionRepository transactionRepository;
    private final TransactionStatusHistoryRepository statusHistoryRepository;
    private final MobiquityService mobiquityService;
    private final ExpressoAdapter expressoAdapter;
    private final ElasticPendingTransactionService elasticPendingService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public Flux<Transaction> searchTransactions(String status, String clientMsisdn,
                                                 String txnId, Instant from, Instant to) {
        if (txnId != null && !txnId.isBlank()) {
            return transactionRepository.findByTxnId(txnId).flux();
        }
        if (clientMsisdn != null && !clientMsisdn.isBlank()) {
            return transactionRepository.findByClientMsisdnOrderByCreatedAtDesc(clientMsisdn);
        }
        return transactionRepository.findAll();
    }

    public Mono<Transaction> getTransactionDetail(String txnId) {
        return transactionRepository.findByTxnId(txnId)
            .switchIfEmpty(Mono.error(new TransactionNotFoundException("Transaction introuvable: " + txnId)));
    }

    public Flux<TransactionStatusHistory> getStatusHistory(String txnId) {
        return transactionRepository.findByTxnId(txnId)
            .switchIfEmpty(Mono.error(new TransactionNotFoundException("Transaction introuvable: " + txnId)))
            .flatMapMany(txn -> statusHistoryRepository.findByTransactionIdOrderByCreatedAtAsc(txn.getId()));
    }

    /**
     * Relance manuelle : appelle getTransactionStatus sur Expresso et met à jour selon le résultat.
     * Ne re-joue JAMAIS requestTopup.
     */
    public Mono<Transaction> manualRetry(String txnId, String operatorLogin) {
        log.info("[BO] Relance manuelle: txnId={}, operator={}", txnId, operatorLogin);

        return transactionRepository.findByTxnId(txnId)
            .switchIfEmpty(Mono.error(new TransactionNotFoundException("Transaction introuvable: " + txnId)))
            .flatMap(transaction -> {
                if (!"PENDING".equals(transaction.getStatus()) && !"FAILED".equals(transaction.getStatus()) && !"EXPIRED".equals(transaction.getStatus())) {
                    return Mono.error(new InvalidTransactionStateException(
                        "Seules les transactions PENDING/FAILED/EXPIRED peuvent être relancées. Statut actuel: " + transaction.getStatus()));
                }

                auditService.log(
                    AuditAction.RETRY, "TRANSACTION", transaction.getTxnId(),
                    "OPERATOR:" + operatorLogin,
                    Map.of("txnId", transaction.getTxnId(), "operator", operatorLogin),
                    null, "INFO", transaction.getCorrelationId());

                return expressoAdapter.getTransactionStatus(transaction.getClientReference(), transaction.getCorrelationId())
                    .flatMap(status -> switch (status) {
                        case "SUCCESS" -> {
                            transaction.setStatus("COMPLETED");
                            transaction.setCompletedAt(Instant.now());
                            transaction.setUpdatedAt(Instant.now());
                            auditService.log(
                                AuditAction.STATUS_CHANGE, "TRANSACTION", transaction.getTxnId(),
                                "OPERATOR:" + operatorLogin,
                                Map.of("oldStatus", "PENDING"),
                                Map.of("newStatus", "COMPLETED"),
                                "SUCCESS", transaction.getCorrelationId());
                            yield transactionRepository.save(transaction)
                                .then(saveHistory(transaction.getId(), "PENDING", "COMPLETED",
                                    "Relance manuelle par " + operatorLogin, "OPERATOR:" + operatorLogin))
                                .then(notificationService.sendSuccessNotification(transaction))
                                .thenReturn(transaction);
                        }
                        case "FAILED" -> {
                            transaction.setStatus("FAILED");
                            transaction.setUpdatedAt(Instant.now());
                            auditService.log(
                                AuditAction.STATUS_CHANGE, "TRANSACTION", transaction.getTxnId(),
                                "OPERATOR:" + operatorLogin,
                                Map.of("oldStatus", "PENDING"),
                                Map.of("newStatus", "FAILED"),
                                "FAILURE", transaction.getCorrelationId());
                            yield transactionRepository.save(transaction)
                                .then(saveHistory(transaction.getId(), "PENDING", "FAILED",
                                    "Échec confirmé par relance manuelle de " + operatorLogin, "OPERATOR:" + operatorLogin))
                                .thenReturn(transaction);
                        }
                        default -> {
                            transaction.setUpdatedAt(Instant.now());
                            yield transactionRepository.save(transaction).thenReturn(transaction);
                        }
                    });
            });
    }

    /**
     * Annulation : uniquement possible pour les transactions CREATED (avant débit).
     */
    public Mono<Transaction> cancelTransaction(String txnId, String operatorLogin) {
        log.info("[BO] Annulation: txnId={}, operator={}", txnId, operatorLogin);

        return transactionRepository.findByTxnId(txnId)
            .switchIfEmpty(Mono.error(new TransactionNotFoundException("Transaction introuvable: " + txnId)))
            .flatMap(transaction -> {
                if (!"CREATED".equals(transaction.getStatus())) {
                    return Mono.error(new InvalidTransactionStateException(
                        "Seules les transactions CREATED peuvent être annulées. Statut actuel: " + transaction.getStatus()));
                }
                transaction.setStatus("FAILED");
                transaction.setFailureReason("Annulée par opérateur: " + operatorLogin);
                transaction.setUpdatedAt(Instant.now());
                auditService.log(
                    AuditAction.BO_INTERVENTION, "TRANSACTION", transaction.getTxnId(),
                    "OPERATOR:" + operatorLogin,
                    Map.of("action", "CANCEL", "operator", operatorLogin),
                    Map.of("newStatus", "FAILED"),
                    "SUCCESS", transaction.getCorrelationId());
                return transactionRepository.save(transaction)
                    .then(saveHistory(transaction.getId(), "CREATED", "FAILED",
                        "Annulation manuelle par " + operatorLogin, "OPERATOR:" + operatorLogin))
                    .thenReturn(transaction);
            });
    }

    /**
     * Remboursement MANUEL — seul point de déclenchement du remboursement dans le système.
     * Appelle Mobiquity refundAccount uniquement si le débit a été confirmé.
     */
    public Mono<Transaction> manualRefund(String txnId, String operatorLogin) {
        log.info("[BO] Remboursement manuel: txnId={}, operator={}", txnId, operatorLogin);

        return transactionRepository.findByTxnId(txnId)
            .switchIfEmpty(Mono.error(new TransactionNotFoundException("Transaction introuvable: " + txnId)))
            .flatMap(transaction -> {
                if (!Boolean.TRUE.equals(transaction.getDebitConfirmed())) {
                    return Mono.error(new InvalidTransactionStateException(
                        "Aucun débit confirmé pour cette transaction: " + txnId));
                }
                if (Boolean.TRUE.equals(transaction.getRefundConfirmed())) {
                    return Mono.error(new InvalidTransactionStateException(
                        "Transaction déjà remboursée: " + txnId));
                }
                if (!"FAILED".equals(transaction.getStatus()) && !"EXPIRED".equals(transaction.getStatus())) {
                    return Mono.error(new InvalidTransactionStateException(
                        "Seules les transactions FAILED/EXPIRED peuvent être remboursées. Statut: " + transaction.getStatus()));
                }

                return mobiquityService.refundAccount(transaction.getTxnId(), transaction.getClientMsisdn(), transaction.getAmount())
                    .flatMap(refundRef -> {
                        transaction.setMobiquityRefundRef(refundRef);
                        transaction.setRefundConfirmed(true);
                        transaction.setStatus("REFUNDED");
                        transaction.setUpdatedAt(Instant.now());
                        auditService.log(
                            AuditAction.REFUND, "TRANSACTION", transaction.getTxnId(),
                            "OPERATOR:" + operatorLogin,
                            Map.of("operator", operatorLogin, "amount", transaction.getAmount()),
                            Map.of("refundRef", refundRef, "status", "REFUNDED"),
                            "SUCCESS", transaction.getCorrelationId());
                        return transactionRepository.save(transaction)
                            .then(saveHistory(transaction.getId(), transaction.getStatus(), "REFUNDED",
                                "Remboursement manuel par " + operatorLogin, "OPERATOR:" + operatorLogin))
                            .thenReturn(transaction);
                    })
                    .onErrorMap(e -> new MobiquityRefundException(
                        "Échec remboursement Mobiquity pour txnId=" + txnId + ": " + e.getMessage(), e));
            });
    }

    private Mono<Void> saveHistory(Long txnId, String oldStatus, String newStatus, String reason, String createdBy) {
        return statusHistoryRepository.save(TransactionStatusHistory.builder()
            .transactionId(txnId)
            .oldStatus(oldStatus)
            .newStatus(newStatus)
            .reason(reason)
            .createdBy(createdBy)
            .build()).then();
    }
}
package sn.mixx.expresso.service.elastic;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ReactiveElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.util.retry.Retry;
import sn.mixx.expresso.domain.Transaction;
import sn.mixx.expresso.domain.TransactionStatusHistory;
import sn.mixx.expresso.domain.elastic.PendingTransactionElastic;
import sn.mixx.expresso.domain.enums.PendingTransactionStatut;
import sn.mixx.expresso.repository.TransactionRepository;
import sn.mixx.expresso.repository.TransactionStatusHistoryRepository;
import sn.mixx.expresso.service.expresso.ExpressoAdapter;
import sn.mixx.expresso.service.notification.NotificationService;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service de gestion du retry pour les transactions PENDING.
 * Clone de ElasticTransactionsFailedService adapté au domaine Expresso.
 *
 * Comportement :
 * - Sélectionne les transactions PENDING éligibles (nextRetryAt <= now, retryCount <= max)
 * - Appelle getTransactionStatus sur Expresso ERS
 * - SUCCESS → COMPLETED + notification
 * - FAILED → FAILED + alerte BO (PAS de remboursement automatique)
 * - PENDING / Timeout → incrément retry + backoff exponentiel
 * - Age > TRANSACTION_TIMEOUT_MS ou retryCount > max → EXPIRED + alerte BO (PAS de remboursement automatique)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticPendingTransactionService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int BATCH_SIZE = 10;
    private static final Duration QUERY_TIMEOUT = Duration.ofSeconds(30);

    @Value("${elasticsearch.default.retry.count:5}")
    private Integer maxRetryCount;

    private final ElasticPendingTransactionRepository pendingRepository;
    private final ReactiveElasticsearchOperations operations;
    private final TransactionRepository transactionRepository;
    private final TransactionStatusHistoryRepository statusHistoryRepository;
    private final ExpressoAdapter expressoAdapter;
    private final NotificationService notificationService;
    private final MeterRegistry meterRegistry;

    @Qualifier("elasticSearchScheduler")
    private final Scheduler elasticSearchScheduler;

    // ==================== Indexation ====================

    public Mono<Void> indexPendingTransaction(Transaction transaction) {
        PendingTransactionElastic doc = PendingTransactionElastic.builder()
            .logId(UUID.randomUUID().toString())
            .txnId(transaction.getTxnId())
            .clientReference(transaction.getClientReference())
            .type(transaction.getType())
            .retryCount(0)
            .maxRetryCount(maxRetryCount)
            .nextRetry(Instant.now().plus(2, ChronoUnit.MINUTES))
            .statut(PendingTransactionStatut.PENDING)
            .clientMsisdn(transaction.getClientMsisdn())
            .beneficiaryMsisdn(transaction.getBeneficiaryMsisdn())
            .amount(transaction.getAmount())
            .correlationId(transaction.getCorrelationId())
            .createdAt(transaction.getCreatedAt())
            .timestamp(Instant.now())
            .build();

        return pendingRepository.save(doc)
            .doOnSuccess(v -> log.info("[PENDING-INDEX] Transaction indexée: txnId={}", transaction.getTxnId()))
            .doOnError(e -> log.error("[PENDING-INDEX] Erreur indexation: {}", e.getMessage()))
            .onErrorResume(e -> Mono.empty())
            .then();
    }

    // ==================== Recherche ====================

    public Flux<PendingTransactionElastic> findTransactionsPendingForRetry() {
        Instant now = Instant.now();

        Criteria criteria = Criteria.where("retryCount").lessThanEqual(maxRetryCount)
            .and("statut").is(PendingTransactionStatut.PENDING.name())
            .and("nextRetry").lessThanEqual(now);

        Query query = new CriteriaQuery(criteria)
            .addSort(Sort.by(Sort.Direction.ASC, "nextRetry"))
            .setPageable(PageRequest.of(0, DEFAULT_PAGE_SIZE));

        return operations.search(query, PendingTransactionElastic.class)
            .subscribeOn(elasticSearchScheduler)
            .timeout(QUERY_TIMEOUT)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                .filter(t -> t instanceof CancellationException || t instanceof TimeoutException)
            )
            .map(SearchHit::getContent)
            .onErrorResume(e -> {
                log.error("[PENDING-QUERY] Erreur recherche transactions: {}", e.getMessage());
                return Flux.empty();
            });
    }

    // ==================== Traitement principal ====================

    public Mono<Void> processTransactionsToRetry() {
        log.info("[RETRY-BATCH] Démarrage traitement transactions PENDING");

        Timer.Sample sample = Timer.start(meterRegistry);
        AtomicInteger batchNumber = new AtomicInteger(0);
        AtomicLong totalProcessed = new AtomicLong(0);
        AtomicLong totalSuccess = new AtomicLong(0);
        AtomicLong totalFailed = new AtomicLong(0);

        return findTransactionsPendingForRetry()
            .buffer(BATCH_SIZE)
            .switchIfEmpty(Flux.defer(() -> {
                log.info("[RETRY-BATCH] Aucune transaction PENDING à traiter");
                return Flux.empty();
            }))
            .concatMap(batch -> processBatch(batch, batchNumber, totalProcessed, totalSuccess, totalFailed))
            .doOnComplete(() -> {
                sample.stop(Timer.builder("retry.transactions.duration").register(meterRegistry));
                logFinalStats(totalProcessed.get(), totalSuccess.get(), totalFailed.get());
            })
            .then();
    }

    private Mono<Void> processBatch(java.util.List<PendingTransactionElastic> batch,
                                     AtomicInteger batchNumber, AtomicLong totalProcessed,
                                     AtomicLong totalSuccess, AtomicLong totalFailed) {
        int currentBatch = batchNumber.incrementAndGet();
        log.info("[RETRY-BATCH] Lot #{} - {} transaction(s)", currentBatch, batch.size());

        return Flux.fromIterable(batch)
            .concatMap(txn -> processOneTransaction(txn)
                .doOnSuccess(success -> {
                    totalProcessed.incrementAndGet();
                    if (Boolean.TRUE.equals(success)) totalSuccess.incrementAndGet();
                    else totalFailed.incrementAndGet();
                })
                .onErrorResume(e -> {
                    log.error("[RETRY] Erreur transaction: logId={}", txn.getLogId(), e);
                    totalFailed.incrementAndGet();
                    return Mono.just(false);
                })
            )
            .then();
    }

    private Mono<Boolean> processOneTransaction(PendingTransactionElastic pending) {
        log.info("[RETRY-TX] Traitement: logId={}, txnId={}", pending.getLogId(), pending.getTxnId());

        return markAsEnCours(pending)
            .then(checkTransactionExpiry(pending))
            .flatMap(expired -> {
                if (Boolean.TRUE.equals(expired)) {
                    return handleExpiredTransaction(pending).thenReturn(false);
                }
                return expressoAdapter.getTransactionStatus(pending.getClientReference(), pending.getCorrelationId())
                    .flatMap(status -> switch (status) {
                        case "SUCCESS" -> handleSuccess(pending).thenReturn(true);
                        case "FAILED" -> handleDefinitiveFailure(pending).thenReturn(false);
                        default -> handleStillPending(pending).thenReturn(false);
                    });
            });
    }

    private Mono<Boolean> checkTransactionExpiry(PendingTransactionElastic pending) {
        boolean tooOld = pending.getCreatedAt() != null &&
            Instant.now().isAfter(pending.getCreatedAt().plus(2, ChronoUnit.HOURS));
        boolean maxRetryReached = pending.getRetryCount() > maxRetryCount;
        return Mono.just(tooOld || maxRetryReached);
    }

    private Mono<Void> handleSuccess(PendingTransactionElastic pending) {
        log.info("[RETRY-SUCCESS] Transaction confirmée SUCCESS: txnId={}", pending.getTxnId());

        pending.setStatut(PendingTransactionStatut.COMPLETED);
        pending.setLastUpdatedAt(Instant.now());

        return operations.save(pending)
            .then(transactionRepository.findByTxnId(pending.getTxnId()))
            .flatMap(transaction -> {
                transaction.setStatus("COMPLETED");
                transaction.setCompletedAt(Instant.now());
                transaction.setUpdatedAt(Instant.now());
                return transactionRepository.save(transaction)
                    .then(saveStatusHistory(transaction.getId(), "PENDING", "COMPLETED",
                        "Confirmé par retry scheduler", "SCHEDULER"))
                    .then(notificationService.sendSuccessNotification(transaction));
            });
    }

    private Mono<Void> handleDefinitiveFailure(PendingTransactionElastic pending) {
        log.warn("[RETRY-FAILED] Échec définitif Expresso → FAILED (alerte BO): txnId={}", pending.getTxnId());

        pending.setStatut(PendingTransactionStatut.FAILED);
        pending.setLastUpdatedAt(Instant.now());

        return operations.save(pending)
            .then(transactionRepository.findByTxnId(pending.getTxnId()))
            .flatMap(transaction -> {
                transaction.setStatus("FAILED");
                transaction.setUpdatedAt(Instant.now());
                return transactionRepository.save(transaction)
                    .then(saveStatusHistory(transaction.getId(), "PENDING", "FAILED",
                        "Échec définitif confirmé par retry scheduler - intervention BO requise", "SCHEDULER"))
                    .then(notificationService.sendFailureNotification(transaction));
            });
    }

    private Mono<Void> handleStillPending(PendingTransactionElastic pending) {
        int newRetryCount = pending.getRetryCount() + 1;
        log.info("[RETRY-PENDING] Toujours en attente, retry {}/{}: txnId={}", newRetryCount, maxRetryCount, pending.getTxnId());

        pending.setRetryCount(newRetryCount);
        pending.setNextRetry(calculateNextRetry(newRetryCount));
        pending.setStatut(PendingTransactionStatut.PENDING);
        pending.setLastUpdatedAt(Instant.now());

        return operations.save(pending)
            .then(transactionRepository.findByTxnId(pending.getTxnId()))
            .flatMap(transaction -> {
                transaction.setRetryCount(newRetryCount);
                transaction.setNextRetryAt(pending.getNextRetry());
                transaction.setUpdatedAt(Instant.now());
                return transactionRepository.save(transaction).then();
            });
    }

    private Mono<Void> handleExpiredTransaction(PendingTransactionElastic pending) {
        log.warn("[RETRY-EXPIRED] Transaction EXPIRÉE → alerte BO (PAS de remboursement auto): txnId={}", pending.getTxnId());

        pending.setStatut(PendingTransactionStatut.EXPIRED);
        pending.setLastUpdatedAt(Instant.now());

        return operations.save(pending)
            .then(transactionRepository.findByTxnId(pending.getTxnId()))
            .flatMap(transaction -> {
                transaction.setStatus("EXPIRED");
                transaction.setExpiredAt(Instant.now());
                transaction.setUpdatedAt(Instant.now());
                transaction.setFailureReason("Transaction expirée - intervention BO requise pour remboursement");
                return transactionRepository.save(transaction)
                    .then(saveStatusHistory(transaction.getId(), "PENDING", "EXPIRED",
                        "Transaction expirée - remboursement manuel BO requis", "SCHEDULER"))
                    .then(notificationService.sendFailureNotification(transaction));
            });
    }

    private Mono<PendingTransactionElastic> markAsEnCours(PendingTransactionElastic pending) {
        pending.setStatut(PendingTransactionStatut.EN_COURS);
        pending.setLastUpdatedAt(Instant.now());
        return pendingRepository.save(pending);
    }

    private Instant calculateNextRetry(int retryCount) {
        int delayMinutes = switch (retryCount) {
            case 1 -> 1;
            case 2 -> 3;
            case 3 -> 5;
            default -> 10;
        };
        return Instant.now().plus(delayMinutes, ChronoUnit.MINUTES);
    }

    private Mono<Void> saveStatusHistory(Long txnId, String oldStatus, String newStatus, String reason, String createdBy) {
        if (txnId == null) return Mono.empty();
        return statusHistoryRepository.save(TransactionStatusHistory.builder()
            .transactionId(txnId)
            .oldStatus(oldStatus)
            .newStatus(newStatus)
            .reason(reason)
            .createdBy(createdBy)
            .build()).then();
    }

    private void logFinalStats(long total, long success, long failed) {
        if (total > 0) {
            double rate = (success * 100.0) / total;
            log.info("[RETRY-BATCH] Stats: total={}, succès={} ({}%), échecs={}",
                total, success, String.format("%.1f", rate), failed);
        } else {
            log.info("[RETRY-BATCH] Aucune transaction traitée");
        }
    }
}
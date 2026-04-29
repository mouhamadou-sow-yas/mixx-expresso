package sn.mixx.expresso.engine.jobs;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import sn.mixx.expresso.service.elastic.ElasticPendingTransactionService;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Job de retry pour les transactions PENDING Expresso.
 * Clone de TransactionsFailedDGIDJobs adapté au domaine Expresso.
 *
 * Cadence : toutes les 60 secondes.
 * Auto-healing : redémarre en cas d'erreur.
 *
 * Ce job appelle UNIQUEMENT getTransactionStatus (jamais requestTopup).
 * Il ne déclenche JAMAIS de remboursement automatique.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PendingTransactionRetryJob {

    private final ElasticPendingTransactionService elasticPendingTransactionService;
    private final AtomicReference<Disposable> jobDisposable = new AtomicReference<>();
    private final Scheduler retryScheduler = Schedulers.newSingle("pending-retry-job");

    @PostConstruct
    public void init() {
        log.info("[RETRY-JOB] Démarrage du job de retry Expresso");
        startRetryJob();
    }

    private void startRetryJob() {
        if (jobDisposable.get() != null && !jobDisposable.get().isDisposed()) {
            return;
        }

        Disposable disposable = Flux.interval(
                Duration.ofSeconds(60),
                Duration.ofSeconds(60),
                retryScheduler
            )
            .publishOn(Schedulers.boundedElastic())
            .flatMap(tick -> executeJob())
            .doOnError(error -> {
                log.error("[RETRY-JOB] Erreur dans le job retry Expresso", error);
                restartJob();
            })
            .retry()
            .subscribe();

        jobDisposable.set(disposable);
        log.info("[RETRY-JOB] Job retry Expresso démarré (intervalle: 60s)");
    }

    private void restartJob() {
        log.info("[RETRY-JOB] Redémarrage du job retry Expresso");
        Disposable disposable = jobDisposable.get();
        if (disposable != null) {
            disposable.dispose();
        }
        startRetryJob();
    }

    public Mono<Void> executeJob() {
        return elasticPendingTransactionService.processTransactionsToRetry();
    }
}
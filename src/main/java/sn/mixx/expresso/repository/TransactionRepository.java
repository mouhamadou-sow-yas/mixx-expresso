package sn.mixx.expresso.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import sn.mixx.expresso.domain.Transaction;

import java.time.Instant;

@Repository
public interface TransactionRepository extends ReactiveCrudRepository<Transaction, Long> {

    Mono<Transaction> findByTxnId(String txnId);

    Mono<Transaction> findByClientReference(String clientReference);

    Flux<Transaction> findByClientMsisdnOrderByCreatedAtDesc(String clientMsisdn);

    @Query("SELECT * FROM transactions WHERE status = 'PENDING' AND next_retry_at <= :now ORDER BY next_retry_at ASC")
    Flux<Transaction> findPendingForRetry(Instant now);

    @Query("SELECT * FROM transactions WHERE status = 'PENDING' AND created_at <= :expiry")
    Flux<Transaction> findExpiredPending(Instant expiry);

    @Query("SELECT COALESCE(SUM(amount + fees), 0) FROM transactions WHERE client_msisdn = :msisdn AND status = 'COMPLETED' AND created_at >= :startOfDay")
    Mono<java.math.BigDecimal> sumDailyAmountByMsisdn(String msisdn, Instant startOfDay);

    @Query("SELECT COALESCE(SUM(amount + fees), 0) FROM transactions WHERE client_msisdn = :msisdn AND status = 'COMPLETED' AND created_at >= :startOfMonth")
    Mono<java.math.BigDecimal> sumMonthlyAmountByMsisdn(String msisdn, Instant startOfMonth);

    @Query("""
        SELECT * FROM transactions
        WHERE client_msisdn = :msisdn
          AND (:status IS NULL OR status = :status)
          AND (:type IS NULL OR type = :type)
          AND (:from IS NULL OR created_at >= :from)
          AND (:to IS NULL OR created_at <= :to)
        ORDER BY created_at DESC
        OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY
        """)
    Flux<Transaction> findByClientMsisdnFiltered(
        String msisdn, String status, String type,
        Instant from, Instant to,
        int offset, int size
    );

    @Query("""
        SELECT COUNT(*) FROM transactions
        WHERE client_msisdn = :msisdn
          AND (:status IS NULL OR status = :status)
          AND (:type IS NULL OR type = :type)
          AND (:from IS NULL OR created_at >= :from)
          AND (:to IS NULL OR created_at <= :to)
        """)
    Mono<Long> countByClientMsisdnFiltered(
        String msisdn, String status, String type,
        Instant from, Instant to
    );
}
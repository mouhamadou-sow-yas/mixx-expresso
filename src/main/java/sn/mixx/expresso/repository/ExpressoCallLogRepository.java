package sn.mixx.expresso.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import sn.mixx.expresso.domain.ExpressoCallLog;

@Repository
public interface ExpressoCallLogRepository extends ReactiveCrudRepository<ExpressoCallLog, Long> {
    Flux<ExpressoCallLog> findByTransactionIdOrderByCreatedAtDesc(Long transactionId);
    Flux<ExpressoCallLog> findByCorrelationId(String correlationId);
}

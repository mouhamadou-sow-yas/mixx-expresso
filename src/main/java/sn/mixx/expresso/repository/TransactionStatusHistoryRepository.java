package sn.mixx.expresso.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import sn.mixx.expresso.domain.TransactionStatusHistory;

@Repository
public interface TransactionStatusHistoryRepository extends ReactiveCrudRepository<TransactionStatusHistory, Long> {
    Flux<TransactionStatusHistory> findByTransactionIdOrderByCreatedAtAsc(Long transactionId);
}

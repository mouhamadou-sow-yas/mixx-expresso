package sn.mixx.expresso.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import sn.mixx.expresso.domain.Notification;

@Repository
public interface NotificationRepository extends ReactiveCrudRepository<Notification, Long> {
    Flux<Notification> findByTransactionId(Long transactionId);
    Flux<Notification> findByStatusOrderByCreatedAtAsc(String status);
}

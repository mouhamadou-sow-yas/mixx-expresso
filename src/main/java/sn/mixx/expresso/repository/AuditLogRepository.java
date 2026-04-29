package sn.mixx.expresso.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import sn.mixx.expresso.domain.AuditLog;

@Repository
public interface AuditLogRepository extends ReactiveCrudRepository<AuditLog, Long> {
    Flux<AuditLog> findByEntityIdOrderByCreatedAtDesc(String entityId);
    Flux<AuditLog> findByCorrelationIdOrderByCreatedAtAsc(String correlationId);
}

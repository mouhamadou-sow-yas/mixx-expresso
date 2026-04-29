package sn.mixx.expresso.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import sn.mixx.expresso.domain.Client;

@Repository
public interface ClientRepository extends ReactiveCrudRepository<Client, Long> {
    Mono<Client> findByMsisdn(String msisdn);
}

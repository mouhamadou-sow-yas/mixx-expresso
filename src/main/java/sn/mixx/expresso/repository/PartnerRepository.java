package sn.mixx.expresso.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import sn.mixx.expresso.domain.Partner;

@Repository
public interface PartnerRepository extends ReactiveCrudRepository<Partner, Long> {
    Mono<Partner> findByCodePartner(String codePartner);
}
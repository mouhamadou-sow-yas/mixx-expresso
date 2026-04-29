package sn.mixx.expresso.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import sn.mixx.expresso.domain.BundleProduct;

@Repository
public interface BundleProductRepository extends ReactiveCrudRepository<BundleProduct, Long> {
    Flux<BundleProduct> findByIsActiveTrueOrderByDisplayOrderAsc();
    Flux<BundleProduct> findByCategoryAndIsActiveTrueOrderByDisplayOrderAsc(String category);
    Mono<BundleProduct> findByErsProductId(String ersProductId);
}

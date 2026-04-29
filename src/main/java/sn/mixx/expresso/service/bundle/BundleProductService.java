package sn.mixx.expresso.service.bundle;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import sn.mixx.expresso.domain.BundleProduct;
import sn.mixx.expresso.exception.BundleNotFoundException;
import sn.mixx.expresso.repository.BundleProductRepository;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class BundleProductService {

    private final BundleProductRepository bundleProductRepository;

    public Flux<BundleProduct> getActiveBundles() {
        return bundleProductRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
    }

    public Flux<BundleProduct> getActiveBundlesByCategory(String category) {
        return bundleProductRepository.findByCategoryAndIsActiveTrueOrderByDisplayOrderAsc(category.toUpperCase());
    }

    public Mono<BundleProduct> getById(Long id) {
        return bundleProductRepository.findById(id)
            .switchIfEmpty(Mono.error(new BundleNotFoundException("Bundle introuvable: " + id)));
    }

    public Mono<BundleProduct> getByErsProductId(String ersProductId) {
        return bundleProductRepository.findByErsProductId(ersProductId)
            .switchIfEmpty(Mono.error(new BundleNotFoundException("Bundle introuvable: " + ersProductId)));
    }

    public Mono<BundleProduct> activate(Long id) {
        return bundleProductRepository.findById(id)
            .switchIfEmpty(Mono.error(new BundleNotFoundException("Bundle introuvable: " + id)))
            .flatMap(product -> {
                product.setIsActive(true);
                product.setUpdatedAt(Instant.now());
                return bundleProductRepository.save(product);
            })
            .doOnSuccess(p -> log.info("[BUNDLE] Activé: id={}, name={}", id, p.getName()));
    }

    public Mono<BundleProduct> deactivate(Long id) {
        return bundleProductRepository.findById(id)
            .switchIfEmpty(Mono.error(new BundleNotFoundException("Bundle introuvable: " + id)))
            .flatMap(product -> {
                product.setIsActive(false);
                product.setUpdatedAt(Instant.now());
                return bundleProductRepository.save(product);
            })
            .doOnSuccess(p -> log.info("[BUNDLE] Désactivé: id={}, name={}", id, p.getName()));
    }
}
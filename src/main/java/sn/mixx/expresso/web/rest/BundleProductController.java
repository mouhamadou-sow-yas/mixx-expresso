package sn.mixx.expresso.web.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import sn.mixx.expresso.domain.BundleProduct;
import sn.mixx.expresso.service.bundle.BundleProductService;

@RestController
@RequestMapping("/v1/bundles")
@RequiredArgsConstructor
public class BundleProductController {

    private final BundleProductService bundleProductService;

    @GetMapping
    public Flux<BundleProduct> getActiveBundles(
            @RequestParam(required = false) String category) {
        if (category != null && !category.isBlank()) {
            return bundleProductService.getActiveBundlesByCategory(category);
        }
        return bundleProductService.getActiveBundles();
    }
    @GetMapping("/{id}")
    public Mono<ResponseEntity<BundleProduct>> getBundle(@PathVariable Long id) {
        return bundleProductService.getById(id).map(ResponseEntity::ok);
    }
}
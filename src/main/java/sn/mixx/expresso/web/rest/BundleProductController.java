package sn.mixx.expresso.web.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import sn.mixx.expresso.domain.BundleProduct;
import sn.mixx.expresso.service.bundle.BundleProductService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BundleProductController {

    private final BundleProductService bundleProductService;

    @GetMapping("/bundles")
    public Flux<BundleProduct> getActiveBundles(
        @RequestParam(required = false) String category) {
        if (category != null && !category.isBlank()) {
            return bundleProductService.getActiveBundlesByCategory(category);
        }
        return bundleProductService.getActiveBundles();
    }

    @GetMapping("/bundles/{id}")
    public Mono<ResponseEntity<BundleProduct>> getBundle(@PathVariable Long id) {
        return bundleProductService.getById(id).map(ResponseEntity::ok);
    }

    @PutMapping("/admin/bundles/{id}/activate")
    public Mono<ResponseEntity<BundleProduct>> activate(@PathVariable Long id) {
        return bundleProductService.activate(id).map(ResponseEntity::ok);
    }

    @PutMapping("/admin/bundles/{id}/deactivate")
    public Mono<ResponseEntity<BundleProduct>> deactivate(@PathVariable Long id) {
        return bundleProductService.deactivate(id).map(ResponseEntity::ok);
    }
}
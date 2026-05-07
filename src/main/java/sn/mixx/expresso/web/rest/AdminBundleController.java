package sn.mixx.expresso.web.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import sn.mixx.expresso.domain.BundleProduct;
import sn.mixx.expresso.service.bundle.BundleProductService;

@RestController
@RequestMapping("/api/admin/bundles")
@RequiredArgsConstructor
public class AdminBundleController {

    private final BundleProductService bundleProductService;

    @PutMapping("/{id}/activate")
    public Mono<ResponseEntity<BundleProduct>> activate(@PathVariable Long id) {
        return bundleProductService.activate(id).map(ResponseEntity::ok);
    }

    @PutMapping("/{id}/deactivate")
    public Mono<ResponseEntity<BundleProduct>> deactivate(@PathVariable Long id) {
        return bundleProductService.deactivate(id).map(ResponseEntity::ok);
    }
}

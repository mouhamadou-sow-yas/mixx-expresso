package sn.mixx.expresso.web.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import sn.mixx.expresso.domain.BundleProduct;
import sn.mixx.expresso.service.bundle.BundleProductService;
import sn.mixx.expresso.service.dto.response.ApiResponse;
import sn.mixx.expresso.service.dto.response.BundleProductResponse;

import java.util.List;

@RestController
@RequestMapping("/v1/bundles")
@RequiredArgsConstructor
public class BundleProductController {

    private final BundleProductService bundleProductService;

    @GetMapping
    public Mono<ResponseEntity<ApiResponse<List<BundleProductResponse>>>> getActiveBundles(
        @RequestParam(required = false) String category,
        @RequestParam(required = false, defaultValue = "true") boolean active) {

        return (category != null && !category.isBlank()
            ? bundleProductService.getActiveBundlesByCategory(category)
            : bundleProductService.getActiveBundles())
            .map(this::toResponse)
            .collectList()
            .map(list -> ResponseEntity.ok(ApiResponse.success(list)));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<ApiResponse<BundleProductResponse>>> getBundle(@PathVariable Long id) {
        return bundleProductService.getById(id)
            .map(product -> ResponseEntity.ok(ApiResponse.success(toResponse(product))));
    }

    private BundleProductResponse toResponse(BundleProduct p) {
        return BundleProductResponse.builder()
            .code(p.getErsProductId())
            .name(p.getName())
            .category(p.getCategory())
            .price(p.getPrice())
            .currency("XOF")
            .validityDays(p.getValidityDays())
            .description(p.getDescription())
            .active(p.getIsActive())
            .autoRenewAvailable(p.getAutoRenewAvailable())
            .build();
    }
}
package sn.mixx.expresso.web.rest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import sn.mixx.expresso.service.RetryConfigService;
import sn.mixx.expresso.service.dto.RetryConfigDTO;

@RestController
@RequestMapping("/api/admin/retry-config")
@RequiredArgsConstructor
public class RetryConfigController {

    private final RetryConfigService retryConfigService;

    @GetMapping
    public Mono<ResponseEntity<RetryConfigDTO>> get() {
        return retryConfigService.get().map(ResponseEntity::ok);
    }

    @PutMapping
    public Mono<ResponseEntity<RetryConfigDTO>> update(@Valid @RequestBody RetryConfigDTO dto) {
        return retryConfigService.update(dto).map(ResponseEntity::ok);
    }
}
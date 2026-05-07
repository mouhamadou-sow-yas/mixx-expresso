package sn.mixx.expresso.web.rest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import sn.mixx.expresso.security.PartnerSecureService;
import sn.mixx.expresso.service.dto.PartnerDTO;

@Slf4j
@RestController
@RequestMapping("/api/partners")
@RequiredArgsConstructor
public class PartnerController {

    private final PartnerSecureService partnerSecureService;

    @PostMapping
    public Mono<ResponseEntity<PartnerDTO>> createPartner(@Valid @RequestBody PartnerDTO partnerDTO) {
        log.info("[API] POST /partners: codePartner={}", partnerDTO.codePartner());
        return partnerSecureService.savePartner(partnerDTO)
            .map(partner -> ResponseEntity.status(HttpStatus.CREATED).body(partner));
    }

    @GetMapping("/{codePartner}")
    public Mono<ResponseEntity<PartnerDTO>> getPartner(@PathVariable String codePartner) {
        return partnerSecureService.getPartner(codePartner)
            .map(ResponseEntity::ok);
    }

    @PutMapping("/{codePartner}/regenerate-keys")
    public Mono<ResponseEntity<PartnerDTO>> regenerateKeys(@PathVariable String codePartner) {
        log.info("[API] PUT /partners/{}/regenerate-keys", codePartner);
        return partnerSecureService.regenerateApiKeys(codePartner)
            .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{codePartner}")
    public Mono<ResponseEntity<Void>> deactivatePartner(@PathVariable String codePartner) {
        log.info("[API] DELETE /partners/{}", codePartner);
        return partnerSecureService.deactivatePartner(codePartner)
            .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }
}
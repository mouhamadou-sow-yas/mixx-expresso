package sn.mixx.expresso.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import sn.mixx.expresso.domain.Partner;
import sn.mixx.expresso.exception.PartnerNotFoundException;
import sn.mixx.expresso.repository.PartnerRepository;
import sn.mixx.expresso.service.dto.PartnerDTO;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerSecureService {

    private final PartnerRepository partnerRepository;
    private final PasswordEncoder passwordEncoder;

    public Mono<PartnerDTO> savePartner(PartnerDTO partnerDTO) {
        log.info("[PARTNER] Traitement partenaire: {}", partnerDTO.codePartner());

        return partnerRepository.findByCodePartner(partnerDTO.codePartner())
            .flatMap(existing -> {
                log.info("[PARTNER] Partenaire existant trouvé: {}", existing.getCodePartner());
                return Mono.just(toDTO(existing));
            })
            .switchIfEmpty(Mono.defer(() -> createNewPartner(partnerDTO)))
            .doOnSuccess(result -> log.info("[PARTNER] Traitement terminé pour: {}", result.codePartner()))
            .onErrorResume(error -> handlePartnerError(error, partnerDTO.codePartner()));
    }

    private Mono<PartnerDTO> createNewPartner(PartnerDTO partnerDTO) {
        log.info("[PARTNER] Création nouveau partenaire: {}", partnerDTO.codePartner());

        String apiKey = generateApiKey();
        String apiSecret = generateApiSecret();

        Partner partner = Partner.builder()
            .codePartner(partnerDTO.codePartner())
            .namePartner(partnerDTO.namePartner())
            .emailAdmin(partnerDTO.emailAdmin())
            .nameAdmin(partnerDTO.nameAdmin())
            .apiKey(apiKey)
            .apiSecret(hashApiSecret(apiSecret))
            .actif(true)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        return partnerRepository.save(partner)
            .map(saved -> PartnerDTO.builder()
                .codePartner(saved.getCodePartner())
                .namePartner(saved.getNamePartner())
                .apiKey(saved.getApiKey())
                .apiSecret(apiSecret)
                .createdAt(saved.getCreatedAt() != null ? saved.getCreatedAt().toString() : null)
                .build())
            .doOnSuccess(dto ->
                log.warn("[PARTNER] IMPORTANT: API Secret généré pour {} - À conserver de manière sécurisée!", dto.codePartner()));
    }

    public Mono<Boolean> validatePartnerCredentials(String codePartner, String apiKey, String apiSecret) {
        log.debug("[PARTNER] Validation credentials pour: {}", codePartner);

        return partnerRepository.findByCodePartner(codePartner)
            .filter(Partner::getActif)
            .flatMap(partner -> {
                if (!partner.getApiKey().equals(apiKey)) {
                    log.warn("[PARTNER] API Key invalide pour: {}", codePartner);
                    return Mono.just(false);
                }
                if (!passwordEncoder.matches(apiSecret, partner.getApiSecret())) {
                    log.warn("[PARTNER] API Secret invalide pour: {}", codePartner);
                    return Mono.just(false);
                }
                log.info("[PARTNER] Credentials valides pour: {}", codePartner);
                return Mono.just(true);
            })
            .onErrorResume(error -> {
                log.error("[PARTNER] Erreur validation credentials", error);
                return Mono.just(false);
            })
            .defaultIfEmpty(false);
    }

    public Mono<PartnerDTO> regenerateApiKeys(String codePartner) {
        log.info("[PARTNER] Régénération clés API pour: {}", codePartner);

        return partnerRepository.findByCodePartner(codePartner)
            .switchIfEmpty(Mono.error(new PartnerNotFoundException("Partenaire non trouvé: " + codePartner)))
            .flatMap(partner -> {
                String newApiKey = generateApiKey();
                String newApiSecret = generateApiSecret();
                partner.setApiKey(newApiKey);
                partner.setApiSecret(hashApiSecret(newApiSecret));
                partner.setUpdatedAt(LocalDateTime.now());
                return partnerRepository.save(partner)
                    .map(saved -> PartnerDTO.builder()
                        .codePartner(saved.getCodePartner())
                        .namePartner(saved.getNamePartner())
                        .apiKey(saved.getApiKey())
                        .apiSecret(newApiSecret)
                        .build());
            })
            .doOnSuccess(dto -> log.warn("[PARTNER] Nouvelles clés générées pour {} - À conserver!", dto.codePartner()));
    }

    public Mono<PartnerDTO> getPartner(String codePartner) {
        return partnerRepository.findByCodePartner(codePartner)
            .map(this::toDTOWithoutSecret)
            .switchIfEmpty(Mono.error(new PartnerNotFoundException("Partenaire non trouvé: " + codePartner)));
    }

    public Mono<Void> deactivatePartner(String codePartner) {
        return partnerRepository.findByCodePartner(codePartner)
            .switchIfEmpty(Mono.error(new PartnerNotFoundException("Partenaire non trouvé: " + codePartner)))
            .flatMap(partner -> {
                partner.setActif(false);
                partner.setUpdatedAt(LocalDateTime.now());
                return partnerRepository.save(partner);
            })
            .then()
            .doOnSuccess(v -> log.info("[PARTNER] Partenaire désactivé: {}", codePartner));
    }

    private String generateApiKey() { return "yas-ak-" + UUID.randomUUID(); }
    private String generateApiSecret() { return "yas-as-" + UUID.randomUUID(); }
    private String hashApiSecret(String apiSecret) { return passwordEncoder.encode(apiSecret); }

    private PartnerDTO toDTO(Partner partner) {
        return PartnerDTO.builder()
            .codePartner(partner.getCodePartner())
            .namePartner(partner.getNamePartner())
            .emailAdmin(partner.getEmailAdmin())
            .nameAdmin(partner.getNameAdmin())
            .apiKey(partner.getApiKey())
            .apiSecret("***MASKED***")
            .actif(partner.getActif())
            .createdAt(partner.getCreatedAt() != null ? partner.getCreatedAt().toString() : null)
            .build();
    }

    private PartnerDTO toDTOWithoutSecret(Partner partner) {
        return PartnerDTO.builder()
            .codePartner(partner.getCodePartner())
            .namePartner(partner.getNamePartner())
            .emailAdmin(partner.getEmailAdmin())
            .nameAdmin(partner.getNameAdmin())
            .apiKey(partner.getApiKey())
            .actif(partner.getActif())
            .createdAt(partner.getCreatedAt() != null ? partner.getCreatedAt().toString() : null)
            .build();
    }

    private Mono<PartnerDTO> handlePartnerError(Throwable error, String codePartner) {
        log.error("[PARTNER] Erreur pour partenaire: {}", codePartner, error);
        if (error instanceof PartnerNotFoundException) return Mono.error(error);
        return Mono.error(new RuntimeException("Erreur lors du traitement du partenaire: " + codePartner, error));
    }
}
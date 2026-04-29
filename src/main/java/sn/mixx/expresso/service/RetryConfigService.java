package sn.mixx.expresso.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import sn.mixx.expresso.domain.RetryConfig;
import sn.mixx.expresso.repository.RetryConfigRepository;
import sn.mixx.expresso.security.SecurityUtils;
import sn.mixx.expresso.service.dto.RetryConfigDTO;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class RetryConfigService {

    private static final long CONFIG_ID = 1L;

    private final RetryConfigRepository retryConfigRepository;

    public Mono<RetryConfigDTO> get() {
        return retryConfigRepository.findById(CONFIG_ID).map(this::toDTO);
    }

    public Mono<RetryConfigDTO> update(RetryConfigDTO dto) {
        return retryConfigRepository.findById(CONFIG_ID)
            .flatMap(config -> SecurityUtils.getCurrentUserLogin().map(login -> {
                config.setMaxAttempts(dto.maxAttempts());
                config.setDelaySeconds(dto.delaySeconds());
                config.setExpressoTimeoutMs(dto.expressoTimeoutMs());
                config.setUpdatedAt(LocalDateTime.now());
                config.setUpdatedBy(login);
                return config;
            }))
            .flatMap(retryConfigRepository::save)
            .doOnSuccess(saved -> log.info("[RetryConfig] Mis à jour: maxAttempts={}, delaySeconds={}, expressoTimeoutMs={}",
                saved.getMaxAttempts(), saved.getDelaySeconds(), saved.getExpressoTimeoutMs()))
            .map(this::toDTO);
    }

    private RetryConfigDTO toDTO(RetryConfig c) {
        return new RetryConfigDTO(
            c.getMaxAttempts(),
            c.getDelaySeconds(),
            c.getExpressoTimeoutMs(),
            c.getUpdatedAt() != null ? c.getUpdatedAt().toString() : null,
            c.getUpdatedBy()
        );
    }
}
package sn.mixx.expresso.service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RetryConfigDTO(
    @NotNull @Min(1) @Max(10)
    Integer maxAttempts,

    @NotNull @Min(1) @Max(300)
    Integer delaySeconds,

    @NotNull @Min(1000) @Max(120000)
    Integer expressoTimeoutMs,

    String updatedAt,
    String updatedBy
) {}
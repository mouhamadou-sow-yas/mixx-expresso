package sn.mixx.expresso.service.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AirtimePurchaseRequest {

    @NotBlank
    private String clientReference;

    @NotBlank
    private String clientMsisdn;

    @NotBlank
    private String beneficiaryMsisdn;

    @NotNull
    @DecimalMin("100")
    @DecimalMax("100000")
    private BigDecimal amount;

    @NotBlank
    private String channel;

    private String correlationId;
}

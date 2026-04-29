package sn.mixx.expresso.service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BundlePurchaseRequest {

    @NotBlank
    private String clientReference;

    @NotBlank
    private String clientMsisdn;

    @NotBlank
    private String beneficiaryMsisdn;

    @NotBlank
    private String ersProductId;

    @NotBlank
    private String channel;

    private String correlationId;
}
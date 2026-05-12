package sn.mixx.expresso.service.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BundlePurchaseRequest {

    @Valid
    @NotNull
    private SenderInfo sender;

    @Valid
    @NotNull
    private ReceiverInfo receiver;

    @Valid
    @NotNull
    private BundleInfo bundle;

    @NotBlank
    private String correlationId;
}
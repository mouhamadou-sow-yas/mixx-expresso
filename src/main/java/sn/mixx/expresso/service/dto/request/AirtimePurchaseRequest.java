package sn.mixx.expresso.service.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AirtimePurchaseRequest {

    @Valid
    @NotNull
    private SenderInfo sender;

    @Valid
    @NotNull
    private AmountInfo amount;

    @Valid
    @NotNull
    private ReceiverInfo receiver;

    @NotBlank
    private String correlationId;
}
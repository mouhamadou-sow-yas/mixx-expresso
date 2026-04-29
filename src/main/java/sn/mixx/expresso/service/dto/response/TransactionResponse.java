package sn.mixx.expresso.service.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionResponse {

    private String txnId;
    private String clientReference;
    private String type;
    private String status;
    private String channel;
    private String clientMsisdn;
    private String beneficiaryMsisdn;
    private BigDecimal amount;
    private BigDecimal fees;
    private String currency;
    private String productId;
    private String productCategory;
    private String ersReference;
    private String failureReason;
    private String correlationId;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;
}
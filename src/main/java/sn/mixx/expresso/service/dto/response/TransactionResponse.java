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
    private String type;
    private String status;
    private BigDecimal amount;
    private String currency;
    private String beneficiaryMsisdn;
    private String ersTransactionId;
    private Integer retryCount;
    private Instant createdAt;
    private Instant completedAt;
    private Instant estimatedCompletion;

    // Bundle uniquement
    private String bundleCode;
    private Integer validityDays;
}
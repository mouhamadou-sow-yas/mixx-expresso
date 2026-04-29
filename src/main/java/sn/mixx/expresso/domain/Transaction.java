package sn.mixx.expresso.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import sn.mixx.expresso.domain.enums.ProductCategory;
import sn.mixx.expresso.domain.enums.TransactionChannel;
import sn.mixx.expresso.domain.enums.TransactionStatus;
import sn.mixx.expresso.domain.enums.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("transactions")
public class Transaction {

    @Id
    private Long id;

    @Column("txn_id")
    private String txnId;

    @Column("client_reference")
    private String clientReference;

    @Column("type")
    private String type;

    @Column("status")
    private String status;

    @Column("channel")
    private String channel;

    @Column("client_msisdn")
    private String clientMsisdn;

    @Column("beneficiary_msisdn")
    private String beneficiaryMsisdn;

    @Column("amount")
    private BigDecimal amount;

    @Column("fees")
    private BigDecimal fees;

    @Column("currency")
    @Builder.Default
    private String currency = "XOF";

    @Column("product_id")
    private String productId;

    @Column("product_category")
    private String productCategory;

    @Column("ers_reference")
    private String ersReference;

    @Column("ers_result_code")
    private Integer ersResultCode;

    @Column("ers_result_description")
    private String ersResultDescription;

    @Column("mobiquity_debit_ref")
    private String mobiquityDebitRef;

    @Column("mobiquity_refund_ref")
    private String mobiquityRefundRef;

    @Column("debit_confirmed")
    @Builder.Default
    private Boolean debitConfirmed = false;

    @Column("refund_confirmed")
    @Builder.Default
    private Boolean refundConfirmed = false;

    @Column("retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    @Column("next_retry_at")
    private Instant nextRetryAt;

    @Column("failure_reason")
    private String failureReason;

    @Column("correlation_id")
    private String correlationId;

    @Column("created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column("updated_at")
    private Instant updatedAt;

    @Column("completed_at")
    private Instant completedAt;

    @Column("expired_at")
    private Instant expiredAt;
}
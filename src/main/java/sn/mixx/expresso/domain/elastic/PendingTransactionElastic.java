package sn.mixx.expresso.domain.elastic;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;
import sn.mixx.expresso.domain.enums.PendingTransactionStatut;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "transactions-pending", createIndex = false)
public class PendingTransactionElastic {

    @Id
    @Field(type = FieldType.Keyword, store = true)
    private String logId;

    @Field(type = FieldType.Keyword)
    private String txnId;

    @Field(type = FieldType.Keyword)
    private String clientReference;

    @Field(type = FieldType.Keyword)
    private String type;

    @Field(type = FieldType.Integer)
    private Integer retryCount;

    @Field(type = FieldType.Integer)
    private Integer maxRetryCount;

    @Field(type = FieldType.Date)
    private Instant nextRetry;

    @Field(type = FieldType.Keyword)
    private PendingTransactionStatut statut;

    @Field(type = FieldType.Keyword)
    private String clientMsisdn;

    @Field(type = FieldType.Keyword)
    private String beneficiaryMsisdn;

    @Field(type = FieldType.Double)
    private BigDecimal amount;

    @Field(type = FieldType.Keyword)
    private String correlationId;

    @Field(type = FieldType.Keyword)
    private String failureReason;

    @Field(type = FieldType.Date)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Field(type = FieldType.Date)
    private Instant lastUpdatedAt;

    @Field(type = FieldType.Date)
    private Instant timestamp;
}
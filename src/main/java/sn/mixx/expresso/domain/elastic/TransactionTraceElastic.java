package sn.mixx.expresso.domain.elastic;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Document Elasticsearch pour la traçabilité complète du cycle de vie des transactions.
 * Index : trace-transactions-expresso
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "trace-transactions-expresso", createIndex = true)
public class TransactionTraceElastic {

    @Id
    @Field(type = FieldType.Keyword, store = true)
    private String logId;

    @Field(type = FieldType.Keyword)
    private String txnId;

    @Field(type = FieldType.Keyword)
    private String clientReference;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Keyword)
    private String type;

    @Field(type = FieldType.Keyword)
    private String channel;

    @Field(type = FieldType.Keyword)
    private String clientMsisdn;

    @Field(type = FieldType.Keyword)
    private String beneficiaryMsisdn;

    @Field(type = FieldType.Double)
    private BigDecimal amount;

    @Field(type = FieldType.Keyword)
    private String productId;

    @Field(type = FieldType.Keyword)
    private String productCategory;

    @Field(type = FieldType.Keyword)
    private String correlationId;

    @Field(type = FieldType.Long)
    private Long processingDurationMs;

    @Field(type = FieldType.Keyword)
    private String ersReference;

    @Field(type = FieldType.Integer)
    private Integer ersResultCode;

    @Field(type = FieldType.Integer)
    private Integer retryCount;

    @Field(type = FieldType.Keyword)
    private String failureReason;

    @Field(type = FieldType.Date)
    private Instant dateInit;

    @Field(type = FieldType.Date)
    private Instant dateDebit;

    @Field(type = FieldType.Date)
    private Instant dateExpresso;

    @Field(type = FieldType.Date)
    private Instant dateCompleted;

    @Field(type = FieldType.Date)
    private Instant dateError;

    @Field(type = FieldType.Date)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Field(type = FieldType.Date)
    private Instant updatedAt;

    @Field(type = FieldType.Date)
    @Builder.Default
    private Instant timestamp = Instant.now();
}
package sn.mixx.expresso.domain.elastic;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.time.Instant;

/**
 * Document Elasticsearch pour la traçabilité des appels SOAP Expresso ERS.
 * Index : expresso-calls-YYYY.MM.DD (rotation quotidienne)
 * ILM : hot 7j → warm 30j → cold 12 mois
 * ID déterministe : <correlation_id>-<operation>-<attempt>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "expresso-calls", createIndex = true)
public class ExpressoCallDocument {

    @Id
    private String id;

    @Field(type = FieldType.Date)
    @Builder.Default
    private Instant timestamp = Instant.now();

    @Field(type = FieldType.Keyword)
    private String correlationId;

    // Transaction info
    @Field(type = FieldType.Keyword, name = "transaction.txn_id")
    private String txnId;

    @Field(type = FieldType.Keyword, name = "transaction.client_reference")
    private String clientReference;

    @Field(type = FieldType.Keyword, name = "transaction.type")
    private String transactionType;

    @Field(type = FieldType.Long, name = "transaction.amount")
    private Long amount;

    // Expresso info
    @Field(type = FieldType.Keyword, name = "expresso.operation")
    private String operation;

    @Field(type = FieldType.Keyword, name = "expresso.endpoint")
    private String endpoint;

    @Field(type = FieldType.Keyword, name = "expresso.product_id")
    private String productId;

    @Field(type = FieldType.Keyword, name = "expresso.beneficiary_msisdn")
    private String beneficiaryMsisdn;

    @Field(type = FieldType.Integer, name = "expresso.attempt_number")
    private Integer attemptNumber;

    // Request info
    @Field(type = FieldType.Keyword, name = "request.soap_action")
    private String soapAction;

    @Field(type = FieldType.Text, name = "request.payload")
    private String requestPayload;

    @Field(type = FieldType.Integer, name = "request.size_bytes")
    private Integer requestSizeBytes;

    // Response info
    @Field(type = FieldType.Text, name = "response.payload")
    private String responsePayload;

    @Field(type = FieldType.Integer, name = "response.size_bytes")
    private Integer responseSizeBytes;

    @Field(type = FieldType.Integer, name = "response.http_status")
    private Integer httpStatus;

    @Field(type = FieldType.Integer, name = "response.ers_result_code")
    private Integer ersResultCode;

    @Field(type = FieldType.Text, name = "response.ers_result_description")
    private String ersResultDescription;

    @Field(type = FieldType.Keyword, name = "response.ers_reference")
    private String ersReference;

    // Performance
    @Field(type = FieldType.Integer)
    private Integer durationMs;

    // Outcome
    @Field(type = FieldType.Keyword)
    private String outcome;

    @Field(type = FieldType.Keyword, name = "error.type")
    private String errorType;

    @Field(type = FieldType.Text, name = "error.message")
    private String errorMessage;

    // Service info
    @Field(type = FieldType.Keyword, name = "host.name")
    private String hostName;

    @Field(type = FieldType.Keyword, name = "service.name")
    @Builder.Default
    private String serviceName = "expresso-adapter";

    @Field(type = FieldType.Keyword, name = "service.version")
    private String serviceVersion;

    @Field(type = FieldType.Keyword)
    private String environment;
}
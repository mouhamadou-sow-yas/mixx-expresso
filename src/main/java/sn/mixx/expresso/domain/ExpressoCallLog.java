package sn.mixx.expresso.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("expresso_call_logs")
public class ExpressoCallLog {

    @Id
    private Long id;

    @Column("transaction_id")
    private Long transactionId;

    @Column("correlation_id")
    private String correlationId;

    @Column("operation")
    private String operation;

    @Column("direction")
    private String direction;

    @Column("http_status")
    private Integer httpStatus;

    @Column("ers_result_code")
    private Integer ersResultCode;

    @Column("duration_ms")
    private Integer durationMs;

    @Column("attempt_number")
    @Builder.Default
    private Integer attemptNumber = 1;

    @Column("es_document_id")
    private String esDocumentId;

    @Column("created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();
}
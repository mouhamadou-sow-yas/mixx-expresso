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
@Table("transaction_status_history")
public class TransactionStatusHistory {

    @Id
    private Long id;

    @Column("transaction_id")
    private Long transactionId;

    @Column("old_status")
    private String oldStatus;

    @Column("new_status")
    private String newStatus;

    @Column("reason")
    private String reason;

    @Column("metadata")
    private String metadata;

    @Column("created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column("created_by")
    private String createdBy;
}
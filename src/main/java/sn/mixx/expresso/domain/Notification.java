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
@Table("notifications")
public class Notification {

    @Id
    private Long id;

    @Column("transaction_id")
    private Long transactionId;

    @Column("type")
    private String type;

    @Column("recipient_msisdn")
    private String recipientMsisdn;

    @Column("template_code")
    private String templateCode;

    @Column("message")
    private String message;

    @Column("status")
    @Builder.Default
    private String status = "PENDING";

    @Column("provider_reference")
    private String providerReference;

    @Column("error_message")
    private String errorMessage;

    @Column("attempts")
    @Builder.Default
    private Integer attempts = 0;

    @Column("sent_at")
    private Instant sentAt;

    @Column("created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();
}
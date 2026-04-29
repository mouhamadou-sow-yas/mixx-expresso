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
@Table("audit_logs")
public class AuditLog {

    @Id
    private Long id;

    @Column("action")
    private String action;

    @Column("entity_type")
    private String entityType;

    @Column("entity_id")
    private String entityId;

    @Column("actor")
    private String actor;

    @Column("request_payload")
    private String requestPayload;

    @Column("response_payload")
    private String responsePayload;

    @Column("result")
    private String result;

    @Column("correlation_id")
    private String correlationId;

    @Column("ip_address")
    private String ipAddress;

    @Column("user_agent")
    private String userAgent;

    @Column("created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();
}
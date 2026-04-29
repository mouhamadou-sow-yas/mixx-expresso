package sn.mixx.expresso.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("clients")
public class Client {

    @Id
    private Long id;

    @Column("msisdn")
    private String msisdn;

    @Column("full_name")
    private String fullName;

    @Column("account_status")
    @Builder.Default
    private String accountStatus = "ACTIVE";

    @Column("daily_limit")
    private BigDecimal dailyLimit;

    @Column("monthly_limit")
    private BigDecimal monthlyLimit;

    @Column("kyc_level")
    private String kycLevel;

    @Column("last_transaction_at")
    private Instant lastTransactionAt;

    @Column("created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column("updated_at")
    private Instant updatedAt;
}
package sn.mixx.expresso.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("retry_config")
public class RetryConfig {

    @Id
    private Long id;

    @Min(1)
    @Max(10)
    @Column("max_attempts")
    private Integer maxAttempts;

    @Min(1)
    @Max(300)
    @Column("delay_seconds")
    private Integer delaySeconds;

    @Min(1000)
    @Max(120000)
    @Column("expresso_timeout_ms")
    private Integer expressoTimeoutMs;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("updated_by")
    private String updatedBy;
}
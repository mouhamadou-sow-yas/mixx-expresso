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
@Table("configuration")
public class AppConfiguration {

    @Id
    private Long id;

    @Column("config_key")
    private String configKey;

    @Column("config_value")
    private String configValue;

    @Column("value_type")
    private String valueType;

    @Column("description")
    private String description;

    @Column("updated_at")
    private Instant updatedAt;

    @Column("updated_by")
    private String updatedBy;
}
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
@Table("bundle_products")
public class BundleProduct {

    @Id
    private Long id;

    @Column("ers_product_id")
    private String ersProductId;

    @Column("category")
    private String category;

    @Column("name")
    private String name;

    @Column("description")
    private String description;

    @Column("price")
    private BigDecimal price;

    @Column("validity_days")
    private Integer validityDays;

    @Column("is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column("auto_renew_available")
    @Builder.Default
    private Boolean autoRenewAvailable = false;

    @Column("display_order")
    @Builder.Default
    private Integer displayOrder = 0;

    @Column("created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column("updated_at")
    private Instant updatedAt;
}
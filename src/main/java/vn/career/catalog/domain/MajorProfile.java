package vn.career.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** How strongly one dimension characterises a major, 0..1. */
@Entity
@Table(name = "major_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MajorProfile {

    @EmbeddedId
    private MajorProfileId id;

    @Column(nullable = false)
    private BigDecimal weight;

    public MajorProfile(UUID majorId, String dimensionCode, BigDecimal weight) {
        this.id = new MajorProfileId(majorId, dimensionCode);
        this.weight = weight;
    }
}

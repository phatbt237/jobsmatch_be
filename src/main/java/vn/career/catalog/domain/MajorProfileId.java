package vn.career.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record MajorProfileId(
        @Column(name = "major_id") UUID majorId,
        @Column(name = "dimension_code") String dimensionCode) implements Serializable {
}

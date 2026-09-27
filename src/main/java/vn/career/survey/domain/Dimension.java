package vn.career.survey.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A measured dimension (RIASEC letter, aptitude or career value). Reference data, keyed by its code. */
@Entity
@Table(name = "dimensions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Dimension {

    @Id
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "group_code", nullable = false)
    private DimensionGroup group;
}

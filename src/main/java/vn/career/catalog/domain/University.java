package vn.career.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.career.common.domain.BaseEntity;

@Entity
@Table(name = "universities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class University extends BaseEntity {

    @Column(nullable = false, updatable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Region region;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UniversityType type;

    /** True for made-up development data. Real data imported later has this set to false. */
    @Column(name = "is_sample", nullable = false)
    private boolean sample;

    public static University create(String code, String name, Region region, UniversityType type) {
        University university = new University();
        university.code = code;
        university.update(name, region, type);
        return university;
    }

    public void update(String name, Region region, UniversityType type) {
        this.name = name;
        this.region = region;
        this.type = type;
    }
}

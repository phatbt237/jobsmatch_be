package vn.career.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.career.common.domain.BaseEntity;

/** A major offered by a university in a given year: exam combination, cutoff score and yearly tuition. */
@Entity
@Table(name = "university_majors")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UniversityMajor extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "university_id", nullable = false, updatable = false)
    private University university;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "major_id", nullable = false, updatable = false)
    private Major major;

    @Column(nullable = false)
    private int year;

    @Column(nullable = false)
    private String combo;

    @Column(name = "cutoff_score")
    private BigDecimal cutoffScore;

    @Column(name = "tuition_per_year")
    private Long tuitionPerYear;

    @Column(name = "is_sample", nullable = false)
    private boolean sample;

    public static UniversityMajor create(University university, Major major, int year, String combo,
                                         BigDecimal cutoffScore, Long tuitionPerYear) {
        UniversityMajor um = new UniversityMajor();
        um.university = university;
        um.major = major;
        um.update(year, combo, cutoffScore, tuitionPerYear);
        return um;
    }

    public void update(int year, String combo, BigDecimal cutoffScore, Long tuitionPerYear) {
        this.year = year;
        this.combo = combo;
        this.cutoffScore = cutoffScore;
        this.tuitionPerYear = tuitionPerYear;
    }
}

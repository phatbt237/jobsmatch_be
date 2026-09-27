package vn.career.survey.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Section D of the survey: practical constraints, one row per attempt (the attempt id is the key).
 * Contains sensitive personal data (grades, family pressure): never log it.
 */
@Entity
@Table(name = "student_constraints")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentConstraints {

    @Id
    @Column(name = "attempt_id")
    private UUID attemptId;

    /** Subject code to average grade, e.g. {"toan": 8.5, "van": 7.0}. */
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, BigDecimal> gpa;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private String[] combos;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "preferred_regions", columnDefinition = "text[]")
    private String[] preferredRegions;

    @Column(name = "budget_per_year")
    private Long budgetPerYear;

    /** 1 (none) to 5 (very strong). */
    @Column(name = "family_pressure")
    private Short familyPressure;

    public StudentConstraints(UUID attemptId) {
        this.attemptId = attemptId;
    }

    /** Full replace: the student always submits the whole of section D. */
    public void replaceWith(Map<String, BigDecimal> gpa, String[] combos, String[] preferredRegions,
                            Long budgetPerYear, Short familyPressure) {
        this.gpa = gpa;
        this.combos = combos;
        this.preferredRegions = preferredRegions;
        this.budgetPerYear = budgetPerYear;
        this.familyPressure = familyPressure;
    }
}

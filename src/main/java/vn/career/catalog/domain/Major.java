package vn.career.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.career.common.domain.BaseEntity;

@Entity
@Table(name = "majors")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Major extends BaseEntity {

    @Column(nullable = false, updatable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "group_name", nullable = false)
    private String groupName;

    @Column(name = "short_description")
    private String shortDescription;

    private String description;

    /** Exam combinations (A00, D01...) accepted by this major. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private String[] combos = new String[0];

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "typical_jobs", nullable = false, columnDefinition = "text[]")
    private String[] typicalJobs = new String[0];

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public static Major create(String code, String name, String groupName, String shortDescription,
                               String description, String[] combos, String[] typicalJobs, boolean active) {
        Major major = new Major();
        major.code = code;
        major.update(name, groupName, shortDescription, description, combos, typicalJobs, active);
        return major;
    }

    public void update(String name, String groupName, String shortDescription, String description,
                       String[] combos, String[] typicalJobs, boolean active) {
        this.name = name;
        this.groupName = groupName;
        this.shortDescription = shortDescription;
        this.description = description;
        this.combos = combos == null ? new String[0] : combos;
        this.typicalJobs = typicalJobs == null ? new String[0] : typicalJobs;
        this.active = active;
    }

    public void deactivate() {
        this.active = false;
    }
}

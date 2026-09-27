package vn.career.catalog.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.career.catalog.domain.MajorProfile;
import vn.career.catalog.domain.MajorProfileId;

public interface MajorProfileRepository extends JpaRepository<MajorProfile, MajorProfileId> {

    @Query("select p from MajorProfile p where p.id.majorId = :majorId")
    List<MajorProfile> findByMajorId(@Param("majorId") UUID majorId);

    @Modifying
    @Query("delete from MajorProfile p where p.id.majorId = :majorId")
    void deleteByMajorId(@Param("majorId") UUID majorId);
}

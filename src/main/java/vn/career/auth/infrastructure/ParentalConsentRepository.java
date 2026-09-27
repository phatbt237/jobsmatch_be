package vn.career.auth.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.career.auth.domain.ParentalConsent;

public interface ParentalConsentRepository extends JpaRepository<ParentalConsent, UUID> {

    List<ParentalConsent> findByStudentId(UUID studentId);

    @Modifying
    @Query("delete from ParentalConsent c where c.student.id = :studentId")
    void deleteByStudentId(@Param("studentId") UUID studentId);
}

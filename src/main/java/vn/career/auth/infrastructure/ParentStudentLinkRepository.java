package vn.career.auth.infrastructure;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.career.auth.domain.LinkStatus;
import vn.career.auth.domain.ParentStudentLink;

public interface ParentStudentLinkRepository extends JpaRepository<ParentStudentLink, UUID> {

    /** Row lock so two parents cannot redeem the same code at the same time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from ParentStudentLink l join fetch l.student where l.inviteCode = :code")
    Optional<ParentStudentLink> findByInviteCodeForUpdate(@Param("code") String code);

    boolean existsByInviteCode(String inviteCode);

    @org.springframework.data.jpa.repository.Modifying
    @Query("delete from ParentStudentLink l where l.student.id = :userId or l.parent.id = :userId")
    void deleteAllOfUser(@Param("userId") UUID userId);

    @Query("select l from ParentStudentLink l where l.student.id = :userId or l.parent.id = :userId order by l.createdAt")
    List<ParentStudentLink> findAllOfUser(@Param("userId") UUID userId);

    Optional<ParentStudentLink> findFirstByStudentIdAndStatusAndInviteExpiresAtAfter(
            UUID studentId, LinkStatus status, Instant now);

    boolean existsByParentIdAndStudentIdAndStatus(UUID parentId, UUID studentId, LinkStatus status);

    @Query("select l from ParentStudentLink l join fetch l.student "
            + "where l.parent.id = :parentId and l.status = :status order by l.updatedAt desc")
    List<ParentStudentLink> findByParentAndStatus(@Param("parentId") UUID parentId,
                                                  @Param("status") LinkStatus status);
}

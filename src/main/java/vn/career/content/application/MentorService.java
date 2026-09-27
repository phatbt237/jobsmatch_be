package vn.career.content.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.career.auth.application.AccountRoleApi;
import vn.career.catalog.application.CatalogQueryApi;
import vn.career.common.api.PageResponse;
import vn.career.common.audit.AuditAction;
import vn.career.common.audit.AuditService;
import vn.career.common.exception.BusinessException;
import vn.career.common.exception.ErrorCode;
import vn.career.common.exception.ForbiddenException;
import vn.career.common.exception.NotFoundException;
import vn.career.content.api.dto.MentorApplyRequest;
import vn.career.content.api.dto.MentorResponse;
import vn.career.content.domain.Mentor;
import vn.career.content.infrastructure.MentorRepository;

/** Mentor onboarding: an adult account applies, an admin verifies. Only verified mentors can publish posts. */
@Service
@RequiredArgsConstructor
public class MentorService {

    private final MentorRepository mentors;
    private final AccountRoleApi accountRoles;
    private final CatalogQueryApi catalog;
    private final AuditService auditService;
    private final Clock clock;

    /**
     * Turns a PARENT (adult) account into an unverified MENTOR. The caller has to refresh their token (or log in
     * again) to get the new role. Students and admins cannot apply.
     */
    @Transactional
    public MentorResponse apply(UUID userId, MentorApplyRequest request) {
        String role = accountRoles.roleOf(userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND, "User not found"));
        if ("MENTOR".equals(role) || mentors.existsById(userId)) {
            throw new BusinessException(ErrorCode.MENTOR_ALREADY_APPLIED, "You have already applied to become a mentor");
        }
        if (!"PARENT".equals(role)) {
            throw new ForbiddenException("Only adult accounts (registered as PARENT) can apply to become a mentor");
        }
        if (request.majorId() != null && !catalog.majorExists(request.majorId())) {
            throw new NotFoundException(ErrorCode.MAJOR_NOT_FOUND, "Major not found");
        }
        Mentor mentor = mentors.saveAndFlush(Mentor.apply(userId, trimToNull(request.company()), request.jobTitle().trim(),
                request.yearsExperience(), request.majorId(), trimToNull(request.bio()), trimToNull(request.linkedinUrl())));
        accountRoles.changeRole(userId, "MENTOR");
        return toResponse(mentor);
    }

    @Transactional(readOnly = true)
    public MentorResponse me(UUID userId) {
        return toResponse(find(userId));
    }

    @Transactional
    public MentorResponse verify(UUID adminId, UUID mentorUserId, Boolean verified) {
        Mentor mentor = find(mentorUserId);
        boolean value = verified == null || verified;
        mentor.setVerified(value, adminId, Instant.now(clock));
        mentors.flush();
        auditService.record(AuditAction.MENTOR_VERIFIED, "Mentor", mentorUserId, Map.of("verified", value));
        return toResponse(mentor);
    }

    @Transactional(readOnly = true)
    public PageResponse<MentorResponse> list(Boolean verified, Pageable pageable) {
        var page = verified == null ? mentors.findAll(pageable) : mentors.findByVerified(verified, pageable);
        return PageResponse.from(page.map(MentorService::toResponse));
    }

    private Mentor find(UUID userId) {
        return mentors.findById(userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.MENTOR_NOT_FOUND, "Mentor profile not found"));
    }

    static MentorResponse toResponse(Mentor m) {
        return new MentorResponse(m.getUserId(), m.getCompany(), m.getJobTitle(), m.getYearsExperience(), m.getMajorId(),
                m.getBio(), m.getLinkedinUrl(), m.isVerified(), m.getVerifiedAt(), m.isSample());
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

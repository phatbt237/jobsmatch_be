package vn.career.auth.application;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.career.auth.api.dto.InviteResponse;
import vn.career.auth.api.dto.LinkedStudentResponse;
import vn.career.auth.domain.LinkStatus;
import vn.career.auth.domain.ParentStudentLink;
import vn.career.auth.domain.ParentalConsent;
import vn.career.auth.domain.User;
import vn.career.auth.infrastructure.ParentStudentLinkRepository;
import vn.career.auth.infrastructure.ParentalConsentRepository;
import vn.career.auth.infrastructure.UserRepository;
import vn.career.common.audit.AuditAction;
import vn.career.common.audit.AuditService;
import vn.career.common.exception.BusinessException;
import vn.career.common.exception.ErrorCode;
import vn.career.common.exception.NotFoundException;
import vn.career.common.web.ClientIp;

/** Parent-student linking: a student creates an invite code, a parent redeems it and gives consent. */
@Service
@RequiredArgsConstructor
public class ParentLinkService {

    private static final String CONSENT_METHOD = "PARENT_INVITE_CODE";
    // No 0/O/1/I so codes are easy to read out or type from a phone.
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;

    private final ParentStudentLinkRepository links;
    private final ParentalConsentRepository consents;
    private final UserRepository users;
    private final ParentInviteProperties inviteProperties;
    private final AuditService auditService;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /** Returns the student's current unexpired invite, or creates one. */
    @Transactional
    public InviteResponse createInvite(UUID studentId) {
        Instant now = Instant.now(clock);
        ParentStudentLink invite = links
                .findFirstByStudentIdAndStatusAndInviteExpiresAtAfter(studentId, LinkStatus.PENDING, now)
                .orElseGet(() -> links.save(ParentStudentLink.invite(
                        users.getReferenceById(studentId), newUniqueCode(), now.plus(inviteProperties.ttl()))));
        return new InviteResponse(invite.getInviteCode(), invite.getInviteExpiresAt());
    }

    @Transactional
    public LinkedStudentResponse acceptInvite(UUID parentId, String inviteCode) {
        ParentStudentLink link = links.findByInviteCodeForUpdate(inviteCode.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new NotFoundException(ErrorCode.INVITE_NOT_FOUND, "Invite code not found"));
        Instant now = Instant.now(clock);

        if (link.getStatus() != LinkStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVITE_ALREADY_USED, "This invite code has already been used");
        }
        if (link.isExpired(now)) {
            throw new BusinessException(ErrorCode.INVITE_EXPIRED, "This invite code has expired");
        }
        User student = link.getStudent();
        if (links.existsByParentIdAndStudentIdAndStatus(parentId, student.getId(), LinkStatus.APPROVED)) {
            throw new BusinessException(ErrorCode.ALREADY_LINKED, "You are already linked to this student");
        }

        User parent = users.getReferenceById(parentId);
        link.approve(parent);
        student.grantParentalConsent();
        consents.save(new ParentalConsent(student, parent, now, CONSENT_METHOD, ClientIp.current()));
        links.saveAndFlush(link);

        auditService.record(AuditAction.PARENT_LINK_APPROVED, "ParentStudentLink", link.getId(),
                Map.of("studentId", student.getId().toString()));
        return toResponse(link);
    }

    @Transactional(readOnly = true)
    public List<LinkedStudentResponse> listStudents(UUID parentId) {
        return links.findByParentAndStatus(parentId, LinkStatus.APPROVED).stream()
                .map(ParentLinkService::toResponse)
                .toList();
    }

    private String newUniqueCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
            }
            code = sb.toString();
        } while (links.existsByInviteCode(code));
        return code;
    }

    private static LinkedStudentResponse toResponse(ParentStudentLink link) {
        User student = link.getStudent();
        return new LinkedStudentResponse(student.getId(), student.getFullName(), student.getStatus(),
                link.getUpdatedAt());
    }
}

package vn.career.auth.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.career.auth.domain.Role;
import vn.career.auth.domain.User;
import vn.career.auth.infrastructure.ParentStudentLinkRepository;
import vn.career.auth.infrastructure.ParentalConsentRepository;
import vn.career.auth.infrastructure.UserRepository;
import vn.career.common.audit.AuditAction;
import vn.career.common.audit.AuditService;
import vn.career.common.exception.BusinessException;
import vn.career.common.exception.ErrorCode;
import vn.career.common.exception.NotFoundException;
import vn.career.common.exception.UnauthorizedException;
import vn.career.common.userdata.UserDataEraser;

/**
 * Deletes an account: every module erases the user's data (survey answers, scores, constraints, recommendations,
 * chat history, posts, Q&A), then the account row is anonymised and can never log in again. It all happens in one
 * transaction and is recorded in the audit log (which keeps only ids, no personal data).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountDeletionService {

    /** Not a valid BCrypt hash, so no password can ever match it. */
    private static final String UNUSABLE_PASSWORD_HASH = "!";

    private final UserRepository users;
    private final ParentStudentLinkRepository links;
    private final ParentalConsentRepository consents;
    private final RefreshTokenService refreshTokens;
    private final List<UserDataEraser> erasers;
    private final AuditService auditService;

    @Transactional
    public void deleteAccount(UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND, "User not found"));
        if (user.isAnonymized()) {
            // the caller still holds an access token of an account that is already gone
            throw new UnauthorizedException(ErrorCode.UNAUTHORIZED, "Account no longer exists");
        }
        if (user.getRole() == Role.ADMIN) {
            throw new BusinessException(ErrorCode.ACCOUNT_DELETE_NOT_ALLOWED, "Admin accounts cannot be deleted through the API");
        }
        Role role = user.getRole();

        // Other modules first: they may still need the user row while erasing
        erasers.forEach(eraser -> eraser.eraseUserData(userId));

        refreshTokens.revokeAll(userId);
        links.deleteAllOfUser(userId);
        consents.deleteByStudentId(userId);
        user.anonymize("deleted-" + userId + User.DELETED_EMAIL_SUFFIX, UNUSABLE_PASSWORD_HASH);
        users.flush();

        auditService.record(AuditAction.ACCOUNT_DELETED, "User", userId, Map.of("role", role.name()));
        log.info("Account {} deleted ({} erasers ran)", userId, erasers.size());
    }
}

package vn.career.auth.application;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.career.auth.domain.ParentStudentLink;
import vn.career.auth.domain.ParentalConsent;
import vn.career.auth.domain.User;
import vn.career.auth.infrastructure.ParentStudentLinkRepository;
import vn.career.auth.infrastructure.ParentalConsentRepository;
import vn.career.auth.infrastructure.UserRepository;
import vn.career.common.audit.AuditAction;
import vn.career.common.audit.AuditService;
import vn.career.common.exception.ErrorCode;
import vn.career.common.exception.NotFoundException;
import vn.career.common.exception.UnauthorizedException;
import vn.career.common.userdata.UserDataExporter;

/**
 * Everything the platform stores about one user as a single JSON document: the account itself plus one section
 * from every module. Password hashes, token hashes and other people's data are never included.
 */
@Service
@RequiredArgsConstructor
public class PersonalDataService {

    private final UserRepository users;
    private final ParentStudentLinkRepository links;
    private final ParentalConsentRepository consents;
    private final List<UserDataExporter> exporters;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional
    public Map<String, Object> export(UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND, "User not found"));
        if (user.isAnonymized()) {
            throw new UnauthorizedException(ErrorCode.UNAUTHORIZED, "Account no longer exists");
        }
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("exportedAt", Instant.now(clock));
        export.put("account", account(user));
        exporters.stream()
                .sorted(java.util.Comparator.comparing(UserDataExporter::section))
                .forEach(exporter -> export.put(exporter.section(), exporter.exportUserData(userId)));
        auditService.record(AuditAction.DATA_EXPORTED, "User", userId, Map.of("sections", export.size() - 1));
        return export;
    }

    private Map<String, Object> account(User user) {
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("id", user.getId());
        account.put("email", user.getEmail());
        account.put("fullName", user.getFullName());
        account.put("dateOfBirth", user.getDateOfBirth());
        account.put("role", user.getRole());
        account.put("status", user.getStatus());
        account.put("createdAt", user.getCreatedAt());
        account.put("parentStudentLinks", links.findAllOfUser(user.getId()).stream().map(PersonalDataService::export).toList());
        account.put("parentalConsents", consents.findByStudentId(user.getId()).stream().map(PersonalDataService::export).toList());
        return account;
    }

    private static Map<String, Object> export(ParentStudentLink link) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", link.getStatus());
        map.put("createdAt", link.getCreatedAt());
        map.put("updatedAt", link.getUpdatedAt());
        return map;
    }

    private static Map<String, Object> export(ParentalConsent consent) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("consentedAt", consent.getConsentedAt());
        map.put("method", consent.getMethod());
        map.put("ipAddress", consent.getIpAddress());
        return map;
    }
}

package vn.career.auth.application;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.career.auth.domain.LinkStatus;
import vn.career.auth.domain.UserStatus;
import vn.career.auth.infrastructure.ParentStudentLinkRepository;
import vn.career.auth.infrastructure.UserRepository;

@Service
@RequiredArgsConstructor
class DefaultAccountAccessApi implements AccountAccessApi {

    private final ParentStudentLinkRepository links;
    private final UserRepository users;

    @Override
    @Transactional(readOnly = true)
    public boolean isApprovedParentOf(UUID parentId, UUID studentId) {
        return links.existsByParentIdAndStudentIdAndStatus(parentId, studentId, LinkStatus.APPROVED);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canUseAiFeatures(UUID userId) {
        return users.findById(userId).map(user -> user.getStatus() == UserStatus.ACTIVE).orElse(false);
    }
}

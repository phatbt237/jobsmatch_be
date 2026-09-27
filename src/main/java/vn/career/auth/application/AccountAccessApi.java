package vn.career.auth.application;

import java.util.UUID;

/**
 * Account questions other modules need answered (survey results, AI chat, Q&A).
 * They must go through this interface instead of touching auth entities or repositories.
 */
public interface AccountAccessApi {

    /** True if the parent has an APPROVED link to the student. */
    boolean isApprovedParentOf(UUID parentId, UUID studentId);

    /** True if the user may use the AI chatbot and community Q&A: ACTIVE, i.e. any required parental consent is given. */
    boolean canUseAiFeatures(UUID userId);
}

package vn.career.common.audit;

/** Actions that must leave an audit trail. Add new values here as later phases need them. */
public enum AuditAction {
    PARENT_LINK_APPROVED,
    SURVEY_PUBLISHED,
    /** A parent or admin looked at a student's survey result. */
    RESULT_VIEWED_BY_OTHER,
    POV_REVIEWED,
    ROLE_CHANGED,
    MENTOR_VERIFIED,
    QA_MODERATED,
    ACCOUNT_DELETED,
    DATA_EXPORTED,
    EMBEDDINGS_REBUILT
}

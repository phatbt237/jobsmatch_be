package vn.career.auth.domain;

public enum UserStatus {
    ACTIVE,
    /** Student below the consent age: may take the survey but not use AI chat or community Q&A yet. */
    PENDING_PARENT_CONSENT,
    LOCKED
}

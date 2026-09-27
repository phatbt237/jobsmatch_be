package vn.career.content.api.dto;

/** Missing {@code verified} means true. Send false to withdraw a verification. */
public record VerifyMentorRequest(Boolean verified) {
}

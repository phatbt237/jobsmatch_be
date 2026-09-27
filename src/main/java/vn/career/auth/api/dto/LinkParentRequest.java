package vn.career.auth.api.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A parent redeems an invite code and explicitly consents to their child using the platform. */
public record LinkParentRequest(
        @NotBlank @Size(max = 32) String inviteCode,
        @AssertTrue(message = "consent must be accepted") boolean consent) {
}

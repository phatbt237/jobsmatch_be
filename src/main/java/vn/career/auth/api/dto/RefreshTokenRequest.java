package vn.career.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of both /auth/refresh and /auth/logout. */
public record RefreshTokenRequest(@NotBlank @Size(max = 256) String refreshToken) {
}

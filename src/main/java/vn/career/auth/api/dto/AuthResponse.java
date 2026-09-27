package vn.career.auth.api.dto;

/** Returned by login and refresh. {@code expiresIn} is the access token lifetime in seconds. */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserResponse user) {
}

package vn.career.auth.api.dto;

import java.time.Instant;

public record InviteResponse(String inviteCode, Instant expiresAt) {
}

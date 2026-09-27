package vn.career.auth.api.dto;

import java.time.Instant;
import java.util.UUID;
import vn.career.auth.domain.UserStatus;

public record LinkedStudentResponse(UUID studentId, String fullName, UserStatus status, Instant linkedAt) {
}

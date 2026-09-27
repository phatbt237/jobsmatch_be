package vn.career.common.security;

import java.util.UUID;

/** The identity extracted from a valid access token. Available to every module via {@link SecurityUtils}. */
public record AuthenticatedUser(UUID id, String role) {
}

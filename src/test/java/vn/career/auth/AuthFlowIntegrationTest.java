package vn.career.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import vn.career.support.AbstractIntegrationTest;

class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    private void assertError(MvcResult result, int status, String code) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        JsonNode body = json(result);
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("data").isNull()).isTrue();
        assertThat(body.get("error").get("code").asText()).isEqualTo(code);
    }

    // ---------- register ----------

    @Test
    void registerStudentOverConsentAgeIsActive() throws Exception {
        String email = uniqueEmail().toUpperCase();

        MvcResult result = register(email, "STUDENT", yearsAgo(17));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode data = json(result).get("data");
        assertThat(data.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(data.get("role").asText()).isEqualTo("STUDENT");
        assertThat(data.get("email").asText()).isEqualTo(email.toLowerCase());
        assertThat(data.has("passwordHash")).isFalse();
        assertThat(data.has("password")).isFalse();
        assertThat(json(result).get("timestamp").asText()).isNotBlank();
    }

    @Test
    void registerStudentUnderConsentAgeIsPendingParentConsent() throws Exception {
        MvcResult result = register(uniqueEmail(), "STUDENT", yearsAgo(15));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(json(result).get("data").get("status").asText()).isEqualTo("PENDING_PARENT_CONSENT");
    }

    @Test
    void registerStudentExactlyAtConsentAgeIsActive() throws Exception {
        MvcResult result = register(uniqueEmail(), "STUDENT", yearsAgo(16));

        assertThat(json(result).get("data").get("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void registerParentIsActive() throws Exception {
        MvcResult result = register(uniqueEmail(), "PARENT", null);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(json(result).get("data").get("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void registerDuplicateEmailIsRejectedCaseInsensitively() throws Exception {
        String email = uniqueEmail();
        register(email, "PARENT", null);

        assertError(register(email.toUpperCase(), "PARENT", null), 422, "EMAIL_ALREADY_EXISTS");
    }

    @Test
    void registerWithInvalidBodyReturnsFieldErrors() throws Exception {
        MvcResult result = post("/api/v1/auth/register",
                Map.of("email", "not-an-email", "password", "short", "fullName", "", "role", "STUDENT"), null);

        assertError(result, 400, "VALIDATION_ERROR");
        JsonNode details = json(result).get("error").get("details");
        assertThat(details).extracting(d -> d.get("field").asText())
                .contains("email", "password", "fullName");
    }

    @Test
    void registerStudentWithoutDateOfBirthIsRejected() throws Exception {
        assertError(register(uniqueEmail(), "STUDENT", null), 400, "VALIDATION_ERROR");
    }

    @Test
    void registerMentorOrAdminIsNotAllowed() throws Exception {
        assertError(register(uniqueEmail(), "MENTOR", null), 422, "ROLE_NOT_ALLOWED");
        assertError(register(uniqueEmail(), "ADMIN", null), 422, "ROLE_NOT_ALLOWED");
    }

    @Test
    void registerWithUnknownRoleIsMalformed() throws Exception {
        assertError(register(uniqueEmail(), "SUPERUSER", null), 400, "MALFORMED_REQUEST");
    }

    // ---------- login ----------

    @Test
    void loginReturnsTokensAndUser() throws Exception {
        String email = uniqueEmail();
        register(email, "STUDENT", yearsAgo(17));

        MvcResult result = login(email, PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode data = json(result).get("data");
        assertThat(data.get("accessToken").asText()).isNotBlank();
        assertThat(data.get("refreshToken").asText()).isNotBlank();
        assertThat(data.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(data.get("expiresIn").asLong()).isEqualTo(900);
        assertThat(data.get("user").get("email").asText()).isEqualTo(email);
    }

    @Test
    void loginWithWrongPasswordAndUnknownEmailGiveTheSameError() throws Exception {
        String email = uniqueEmail();
        register(email, "PARENT", null);

        assertError(login(email, "WrongPassw0rd"), 401, "INVALID_CREDENTIALS");
        assertError(login(uniqueEmail(), PASSWORD), 401, "INVALID_CREDENTIALS");
    }

    @Test
    void loginIsTemporarilyLockedAfterFiveFailures() throws Exception {
        String email = uniqueEmail();
        register(email, "PARENT", null);

        for (int i = 0; i < 5; i++) {
            assertError(login(email, "WrongPassw0rd"), 401, "INVALID_CREDENTIALS");
        }

        // Even the correct password is refused while locked
        assertError(login(email, PASSWORD), 429, "TOO_MANY_LOGIN_ATTEMPTS");
    }

    @Test
    void successfulLoginResetsTheFailureCounter() throws Exception {
        String email = uniqueEmail();
        register(email, "PARENT", null);

        for (int i = 0; i < 4; i++) {
            login(email, "WrongPassw0rd");
        }
        assertThat(login(email, PASSWORD).getResponse().getStatus()).isEqualTo(200);
        for (int i = 0; i < 4; i++) {
            assertError(login(email, "WrongPassw0rd"), 401, "INVALID_CREDENTIALS");
        }
    }

    // ---------- /me ----------

    @Test
    void meReturnsTheAuthenticatedAccount() throws Exception {
        String email = uniqueEmail();
        register(email, "PARENT", null);
        String token = json(login(email, PASSWORD)).get("data").get("accessToken").asText();

        MvcResult result = get("/api/v1/me", token);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(result).get("data").get("email").asText()).isEqualTo(email);
    }

    // ---------- refresh / logout ----------

    @Test
    void refreshRotatesTheTokenAndRevokesTheOldOne() throws Exception {
        JsonNode session = registerAndLogin("PARENT", null);
        String oldRefresh = session.get("refreshToken").asText();

        MvcResult refreshed = post("/api/v1/auth/refresh", Map.of("refreshToken", oldRefresh), null);

        assertThat(refreshed.getResponse().getStatus()).isEqualTo(200);
        JsonNode data = json(refreshed).get("data");
        String newRefresh = data.get("refreshToken").asText();
        assertThat(newRefresh).isNotEqualTo(oldRefresh);
        // the new access token works
        assertThat(get("/api/v1/me", data.get("accessToken").asText()).getResponse().getStatus()).isEqualTo(200);
        // the old refresh token no longer does
        assertError(post("/api/v1/auth/refresh", Map.of("refreshToken", oldRefresh), null),
                401, "REFRESH_TOKEN_INVALID");
    }

    @Test
    void reusingARevokedRefreshTokenRevokesTheWholeSession() throws Exception {
        JsonNode session = registerAndLogin("PARENT", null);
        String oldRefresh = session.get("refreshToken").asText();
        JsonNode rotated = json(post("/api/v1/auth/refresh", Map.of("refreshToken", oldRefresh), null)).get("data");
        String newRefresh = rotated.get("refreshToken").asText();

        // Attacker (or bug) replays the old token: rejected and the whole chain is burned
        assertError(post("/api/v1/auth/refresh", Map.of("refreshToken", oldRefresh), null),
                401, "REFRESH_TOKEN_INVALID");

        assertError(post("/api/v1/auth/refresh", Map.of("refreshToken", newRefresh), null),
                401, "REFRESH_TOKEN_INVALID");
    }

    @Test
    void refreshWithUnknownTokenIsRejected() throws Exception {
        assertError(post("/api/v1/auth/refresh", Map.of("refreshToken", "does-not-exist"), null),
                401, "REFRESH_TOKEN_INVALID");
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        JsonNode session = registerAndLogin("PARENT", null);
        String access = session.get("accessToken").asText();
        String refresh = session.get("refreshToken").asText();

        MvcResult logout = post("/api/v1/auth/logout", Map.of("refreshToken", refresh), access);

        assertThat(logout.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(logout).get("success").asBoolean()).isTrue();
        assertError(post("/api/v1/auth/refresh", Map.of("refreshToken", refresh), null),
                401, "REFRESH_TOKEN_INVALID");
    }

    @Test
    void logoutCannotRevokeSomeoneElsesToken() throws Exception {
        JsonNode alice = registerAndLogin("PARENT", null);
        JsonNode bob = registerAndLogin("PARENT", null);

        // Bob tries to log out using Alice's refresh token
        post("/api/v1/auth/logout", Map.of("refreshToken", alice.get("refreshToken").asText()),
                bob.get("accessToken").asText());

        MvcResult stillWorks = post("/api/v1/auth/refresh",
                Map.of("refreshToken", alice.get("refreshToken").asText()), null);
        assertThat(stillWorks.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void logoutRequiresAuthentication() throws Exception {
        assertError(post("/api/v1/auth/logout", Map.of("refreshToken", "whatever"), null), 401, "UNAUTHORIZED");
    }
}

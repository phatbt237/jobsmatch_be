package vn.career.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import vn.career.support.AbstractIntegrationTest;

/** 401 for missing/bad tokens, 403 for the wrong role, and the shared response envelope. */
class SecurityAccessIntegrationTest extends AbstractIntegrationTest {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    private void assertError(MvcResult result, int status, String code) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        JsonNode body = json(result);
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("error").get("code").asText()).isEqualTo(code);
        assertThat(body.get("error").get("message").asText()).isNotBlank();
    }

    private String tokenExpiringAt(Instant expiry) {
        Instant issued = expiry.minusSeconds(900);
        return Jwts.builder()
                .issuer("career-backend")
                .subject(UUID.randomUUID().toString())
                .claim("role", "STUDENT")
                .issuedAt(Date.from(issued))
                .expiration(Date.from(expiry))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    // ---------- 401 ----------

    @Test
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        assertError(get("/api/v1/me", null), 401, "UNAUTHORIZED");
    }

    @Test
    void protectedEndpointWithGarbageTokenReturns401() throws Exception {
        assertError(get("/api/v1/me", "garbage"), 401, "TOKEN_INVALID");
    }

    @Test
    void protectedEndpointWithExpiredTokenReturns401() throws Exception {
        assertError(get("/api/v1/me", tokenExpiringAt(Instant.now().minusSeconds(60))), 401, "TOKEN_EXPIRED");
    }

    @Test
    void tokenSignedWithAnotherKeyReturns401() throws Exception {
        String forged = Jwts.builder()
                .issuer("career-backend")
                .subject(UUID.randomUUID().toString())
                .claim("role", "ADMIN")
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor("another-secret-0123456789-0123456789-xx".getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertError(get("/api/v1/me", forged), 401, "TOKEN_INVALID");
    }

    @Test
    void validTokenOfDeletedAccountCannotReadMe() throws Exception {
        assertError(get("/api/v1/me", tokenExpiringAt(Instant.now().plusSeconds(600))), 401, "UNAUTHORIZED");
    }

    // ---------- 403 ----------

    @Test
    void parentCannotCreateStudentInvite() throws Exception {
        String parentToken = registerAndLogin("PARENT", null).get("accessToken").asText();

        assertError(post("/api/v1/parent/invites", Map.of(), parentToken), 403, "ACCESS_DENIED");
    }

    @Test
    void studentCannotUseParentEndpoints() throws Exception {
        String studentToken = registerAndLogin("STUDENT", yearsAgo(17)).get("accessToken").asText();

        assertError(get("/api/v1/parent/students", studentToken), 403, "ACCESS_DENIED");
        assertError(post("/api/v1/parent/links", Map.of("inviteCode", "ABCDEFGH", "consent", true), studentToken),
                403, "ACCESS_DENIED");
    }

    // ---------- envelope / infrastructure ----------

    @Test
    void unknownRouteReturnsStandardNotFound() throws Exception {
        String token = registerAndLogin("PARENT", null).get("accessToken").asText();

        assertError(get("/api/v1/does-not-exist", token), 404, "NOT_FOUND");
    }

    @Test
    void wrongHttpMethodReturnsStandardMethodNotAllowed() throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/auth/login")
                .header("Authorization", "Bearer " + registerAndLogin("PARENT", null).get("accessToken").asText()))
                .andReturn();

        assertError(result, 405, "METHOD_NOT_ALLOWED");
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                .contentType("application/json")
                .content("{ not json"))
                .andReturn();

        assertError(result, 400, "MALFORMED_REQUEST");
    }

    @Test
    void everyResponseCarriesATraceId() throws Exception {
        MvcResult generated = get("/api/v1/me", null);
        assertThat(generated.getResponse().getHeader("X-Trace-Id")).isNotBlank();

        MvcResult echoed = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/me")
                .header("X-Trace-Id", "client-trace-12345")).andReturn();
        assertThat(echoed.getResponse().getHeader("X-Trace-Id")).isEqualTo("client-trace-12345");

        MvcResult rejected = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/me")
                .header("X-Trace-Id", "bad id\nwith newline")).andReturn();
        assertThat(rejected.getResponse().getHeader("X-Trace-Id")).doesNotContain("\n").isNotEqualTo("bad id\nwith newline");
    }

    @Test
    void swaggerDocsArePublicInTestProfile() throws Exception {
        assertThat(get("/v3/api-docs", null).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void corsPreflightFromAllowedOriginSucceeds() throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.options("/api/v1/auth/login")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin")).isEqualTo("http://localhost:5173");
    }

    @Test
    void corsPreflightFromUnknownOriginIsRejected() throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.options("/api/v1/auth/login")
                .header("Origin", "http://evil.example.com")
                .header("Access-Control-Request-Method", "POST")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }
}

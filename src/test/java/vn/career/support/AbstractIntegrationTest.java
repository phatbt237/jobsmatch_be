package vn.career.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Boots the full app once (context is cached) against real PostgreSQL and Redis containers. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
public abstract class AbstractIntegrationTest {

    protected static final String PASSWORD = "Passw0rd123";
    protected static final String ADMIN_EMAIL = "admin@example.com";
    protected static final String ADMIN_PASSWORD = "AdminPassw0rd";
    protected static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;

    /** Every test uses its own email so tests never interfere with each other. */
    protected static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    protected static LocalDate yearsAgo(int years) {
        return LocalDate.now(VN).minusYears(years);
    }

    protected MvcResult send(HttpMethod method, String path, Object body, String bearerToken) throws Exception {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.request(method, path);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        }
        if (bearerToken != null) {
            request.header("Authorization", "Bearer " + bearerToken);
        }
        return mockMvc.perform(request).andReturn();
    }

    protected MvcResult post(String path, Object body, String bearerToken) throws Exception {
        return send(HttpMethod.POST, path, body == null ? Map.of() : body, bearerToken);
    }

    protected MvcResult put(String path, Object body, String bearerToken) throws Exception {
        return send(HttpMethod.PUT, path, body, bearerToken);
    }

    protected MvcResult delete(String path, String bearerToken) throws Exception {
        return send(HttpMethod.DELETE, path, null, bearerToken);
    }

    protected MvcResult get(String path, String bearerToken) throws Exception {
        return send(HttpMethod.GET, path, null, bearerToken);
    }

    protected JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected MvcResult register(String email, String role, LocalDate dateOfBirth) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("email", email);
        body.put("password", PASSWORD);
        body.put("fullName", "Test " + role);
        body.put("dateOfBirth", dateOfBirth == null ? null : dateOfBirth.toString());
        body.put("role", role);
        return post("/api/v1/auth/register", body, null);
    }

    protected MvcResult login(String email, String password) throws Exception {
        return post("/api/v1/auth/login", Map.of("email", email, "password", password), null);
    }

    /** Registers a new user and logs in, returning the "data" node of the login response. */
    protected JsonNode registerAndLogin(String role, LocalDate dateOfBirth) throws Exception {
        String email = uniqueEmail();
        register(email, role, dateOfBirth);
        return loginOrFail(email, PASSWORD);
    }

    protected JsonNode loginOrFail(String email, String password) throws Exception {
        MvcResult result = login(email, password);
        if (result.getResponse().getStatus() != 200) {
            throw new IllegalStateException("Login failed with " + result.getResponse().getStatus());
        }
        return json(result).get("data");
    }

    /** Access token of the bootstrap admin configured in application-test.yml. */
    protected String adminToken() throws Exception {
        return loginOrFail(ADMIN_EMAIL, ADMIN_PASSWORD).get("accessToken").asText();
    }

    protected String studentToken() throws Exception {
        return registerAndLogin("STUDENT", yearsAgo(17)).get("accessToken").asText();
    }
}

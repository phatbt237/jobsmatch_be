package vn.career.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import vn.career.support.SurveyTestSupport.Persona;

/** Adds "a student who filled in the whole survey" helpers on top of the integration test base. */
public abstract class AbstractStudentFlowTest extends AbstractIntegrationTest {

    @Autowired
    protected JdbcTemplate jdbc;

    protected SurveyTestSupport survey;

    @BeforeEach
    void createSurveySupport() {
        survey = new SurveyTestSupport(jdbc, objectMapper);
    }

    /** A student with a started attempt. {@code attemptId} is known before anything is submitted. */
    public record Student(String userId, String email, String token, String attemptId) {
    }

    protected JsonNode data(MvcResult result, int status) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        return json(result).get("data");
    }

    protected void assertError(MvcResult result, int status, String code) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        assertThat(json(result).get("error").get("code").asText()).isEqualTo(code);
    }

    protected static Map<String, Object> defaultConstraints() {
        return Map.of("gpa", Map.of("toan", 8.5, "van", 7.5, "anh", 8.0, "ly", 8.0, "hoa", 7.0, "sinh", 7.0,
                "su", 7.0, "dia", 7.0), "combos", List.of("A00", "A01", "D01"), "preferredRegions", List.of("NORTH", "SOUTH"),
                "budgetPerYear", 60_000_000, "familyPressure", 2);
    }

    /** Registers a student of the given age, starts an attempt and fills in answers and constraints as given. */
    protected Student startedStudent(int age, Persona persona, Map<String, Object> constraints) throws Exception {
        JsonNode session = registerAndLogin("STUDENT", yearsAgo(age));
        String token = session.get("accessToken").asText();
        String attemptId = data(post("/api/v1/attempts", null, token), 201).get("id").asText();
        if (persona != null) {
            data(put("/api/v1/attempts/" + attemptId + "/answers", Map.of("answers", survey.answers(1, persona)), token), 200);
        }
        if (constraints != null) {
            data(put("/api/v1/attempts/" + attemptId + "/constraints", constraints, token), 200);
        }
        return new Student(session.get("user").get("id").asText(), session.get("user").get("email").asText(), token, attemptId);
    }

    protected Student startedStudent(Persona persona, Map<String, Object> constraints) throws Exception {
        return startedStudent(17, persona, constraints);
    }

    /** A student who completed everything and is ready to submit. */
    protected Student readyStudent() throws Exception {
        return startedStudent(Persona.of(2, "I", 5, "C", 5, "LOGIC", 5), defaultConstraints());
    }

    protected JsonNode submit(Student student) throws Exception {
        return data(post("/api/v1/attempts/" + student.attemptId() + "/submit", null, student.token()), 200);
    }

    protected JsonNode result(Student student) throws Exception {
        return data(get("/api/v1/attempts/" + student.attemptId() + "/result", student.token()), 200);
    }
}

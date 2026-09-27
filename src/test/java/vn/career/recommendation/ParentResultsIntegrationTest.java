package vn.career.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.career.support.AbstractStudentFlowTest;

/** A parent finds the attempt ids of a linked child, then opens the (audited) result. */
class ParentResultsIntegrationTest extends AbstractStudentFlowTest {

    private String linkedParent(Student student) throws Exception {
        JsonNode parent = registerAndLogin("PARENT", null);
        String token = parent.get("accessToken").asText();
        String code = data(post("/api/v1/parent/invites", null, student.token()), 200).get("inviteCode").asText();
        data(post("/api/v1/parent/links", Map.of("inviteCode", code, "consent", true), token), 200);
        return token;
    }

    @Test
    void aLinkedParentSeesTheFinishedAttemptsOfTheChildNewestFirst() throws Exception {
        Student student = readyStudent();
        submit(student);
        String parent = linkedParent(student);

        JsonNode list = data(get("/api/v1/parent/students/" + student.userId() + "/results", parent), 200);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("attemptId").asText()).isEqualTo(student.attemptId());
        assertThat(list.get(0).get("submittedAt").asText()).isNotBlank();
        assertThat(list.get(0).get("surveyVersion").asInt()).isEqualTo(1);
        // and the id can be used to open the result
        assertThat(data(get("/api/v1/attempts/" + list.get(0).get("attemptId").asText() + "/result", parent), 200)
                .get("recommendations")).hasSize(5);
    }

    @Test
    void unfinishedAttemptsAreNotListed() throws Exception {
        Student student = readyStudent();   // answered everything but did not submit
        String parent = linkedParent(student);

        assertThat(data(get("/api/v1/parent/students/" + student.userId() + "/results", parent), 200)).isEmpty();
    }

    @Test
    void onlyTheLinkedParentMayListTheResults() throws Exception {
        Student student = readyStudent();
        submit(student);
        linkedParent(student);
        String stranger = registerAndLogin("PARENT", null).get("accessToken").asText();
        String path = "/api/v1/parent/students/" + student.userId() + "/results";

        assertError(get(path, stranger), 403, "ACCESS_DENIED");
        assertError(get(path, student.token()), 403, "ACCESS_DENIED");
        assertError(get(path, adminToken()), 403, "ACCESS_DENIED");
        assertError(get(path, null), 401, "UNAUTHORIZED");
    }
}

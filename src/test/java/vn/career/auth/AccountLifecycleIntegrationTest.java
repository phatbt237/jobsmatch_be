package vn.career.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;
import vn.career.ai.infrastructure.VectorStore;
import vn.career.common.audit.AuditLog;
import vn.career.common.audit.AuditLogRepository;
import vn.career.support.AbstractStudentFlowTest;

/** "Delete my account" and "export my data" across every module. */
class AccountLifecycleIntegrationTest extends AbstractStudentFlowTest {

    @Autowired
    private AuditLogRepository auditLogs;
    @Autowired
    private VectorStore vectorStore;

    // ---------- helpers ----------

    private int count(String sql, String userId) {
        return jdbc.queryForObject(sql, Integer.class, userId);
    }

    private String majorId(String code) {
        return jdbc.queryForObject("select id::text from majors where code = ?", String.class, code);
    }

    /** A student who did everything: survey, submit, chat, Q&A and a linked parent. */
    private record ActiveStudent(Student student, String parentToken, String parentId, String threadId) {
    }

    private ActiveStudent fullyActiveStudent() throws Exception {
        Student student = readyStudent();
        submit(student);
        String major = majorId("IT");
        data(post("/api/v1/chat", Map.of("message", "Ngành công nghệ thông tin học những gì?", "majorId", major), student.token()), 200);
        data(post("/api/v1/chat", Map.of("message", "Nghề lập trình viên có áp lực không?"), student.token()), 200);
        String threadId = data(post("/api/v1/majors/" + major + "/qa", Map.of("title", "Câu hỏi của em", "content", "Nội dung riêng tư của em"),
                student.token()), 201).get("id").asText();
        data(post("/api/v1/qa/" + threadId + "/answers", Map.of("content", "Em tự trả lời"), student.token()), 201);

        JsonNode parent = registerAndLogin("PARENT", null);
        String code = data(post("/api/v1/parent/invites", null, student.token()), 200).get("inviteCode").asText();
        data(post("/api/v1/parent/links", Map.of("inviteCode", code, "consent", true), parent.get("accessToken").asText()), 200);
        return new ActiveStudent(student, parent.get("accessToken").asText(), parent.get("user").get("id").asText(), threadId);
    }

    private MvcResult patch(String path, Object body, String token) throws Exception {
        return send(HttpMethod.PATCH, path, body, token);
    }

    // ---------- export ----------

    @Test
    void exportContainsEverythingTheStudentEnteredAndReceived() throws Exception {
        ActiveStudent s = fullyActiveStudent();
        // wait for the explanations so they are part of the export
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(result(s.student()).get("explanationStatus").asText()).isEqualTo("DONE"));

        MvcResult response = get("/api/v1/me/export", s.student().token());
        JsonNode export = data(response, 200);

        assertThat(response.getResponse().getHeader("Content-Disposition")).contains("attachment").contains("my-data.json");
        assertThat(export.get("exportedAt").asText()).isNotBlank();
        // account
        JsonNode account = export.get("account");
        assertThat(account.get("email").asText()).isEqualTo(s.student().email());
        assertThat(account.get("fullName").asText()).isEqualTo("Test STUDENT");
        assertThat(account.get("dateOfBirth").asText()).isNotBlank();
        assertThat(account.get("role").asText()).isEqualTo("STUDENT");
        assertThat(account.get("parentStudentLinks")).hasSize(1);
        assertThat(account.get("parentalConsents")).hasSize(1);
        // survey
        JsonNode attempts = export.get("surveyAttempts");
        assertThat(attempts).hasSize(1);
        assertThat(attempts.get(0).get("status").asText()).isEqualTo("SCORED");
        assertThat(attempts.get(0).get("answers")).hasSize(70);
        assertThat(attempts.get(0).get("answers").get(0).get("question").asText()).isNotBlank();
        assertThat(attempts.get(0).get("constraints").get("familyPressure").asInt()).isEqualTo(2);
        assertThat(attempts.get(0).get("constraints").get("gpa").get("toan").decimalValue()).isEqualByComparingTo("8.5");
        // results
        assertThat(export.get("dimensionScores").get(0).get("scores")).hasSize(19);
        JsonNode recommendation = export.get("recommendations").get(0);
        assertThat(recommendation.get("majors")).hasSize(5);
        assertThat(recommendation.get("majors").get(0).get("explanation").asText()).isNotBlank();
        assertThat(recommendation.get("summary").asText()).isNotBlank();
        // chat and community
        assertThat(export.get("chatMessages")).hasSize(4);
        assertThat(export.get("chatMessages").get(0).get("role").asText()).isEqualTo("USER");
        assertThat(export.get("content").get("questions")).hasSize(1);
        assertThat(export.get("content").get("questions").get(0).get("content").asText()).isEqualTo("Nội dung riêng tư của em");
        assertThat(export.get("content").get("answers")).hasSize(1);
        assertThat(export.get("content").get("mentorProfile").isNull()).isTrue();
    }

    @Test
    void exportNeverContainsSecretsOrOtherPeoplesData() throws Exception {
        ActiveStudent s = fullyActiveStudent();
        String other = readyStudent().email();

        String raw = get("/api/v1/me/export", s.student().token()).getResponse().getContentAsString();

        assertThat(raw).doesNotContain("passwordHash", "password_hash", "tokenHash", "refreshToken", "$2a$", "$2b$");
        assertThat(raw).doesNotContain(other);
        assertThat(raw).doesNotContain("\"invite");
    }

    @Test
    void aParentsExportHoldsTheirOwnDataOnly() throws Exception {
        ActiveStudent s = fullyActiveStudent();

        JsonNode export = data(get("/api/v1/me/export", s.parentToken()), 200);

        assertThat(export.get("account").get("role").asText()).isEqualTo("PARENT");
        assertThat(export.get("account").get("parentStudentLinks")).hasSize(1);
        assertThat(export.get("surveyAttempts")).isEmpty();
        assertThat(export.get("chatMessages")).isEmpty();
        assertThat(export.toString()).doesNotContain(s.student().email()).doesNotContain("Nội dung riêng tư");
    }

    @Test
    void exportingIsAudited() throws Exception {
        Student student = readyStudent();

        data(get("/api/v1/me/export", student.token()), 200);

        List<AuditLog> audit = auditLogs.findByEntityTypeAndEntityId("User", UUID.fromString(student.userId()));
        assertThat(audit).extracting(AuditLog::getAction).contains("DATA_EXPORTED");
        assertThat(audit.stream().filter(a -> a.getAction().equals("DATA_EXPORTED")).findFirst().orElseThrow().getActorId())
                .isEqualTo(UUID.fromString(student.userId()));
    }

    // ---------- delete ----------

    @Test
    void deletingAStudentRemovesEverythingTheyStoredAndAnonymisesTheAccount() throws Exception {
        ActiveStudent s = fullyActiveStudent();
        Student student = s.student();
        String userId = student.userId();
        // another student whose data must stay untouched
        Student bystander = readyStudent();
        submit(bystander);
        // wait until nothing is running in the background for the doomed attempt
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(result(student).get("explanationStatus").asText()).isEqualTo("DONE"));

        assertThat(data(delete("/api/v1/me", student.token()), 200).isNull()).isTrue();

        // survey, results, chat, community: all gone
        assertThat(count("select count(*) from survey_attempts where user_id = ?::uuid", userId)).isZero();
        assertThat(count("select count(*) from answers a join survey_attempts t on t.id = a.attempt_id where t.user_id = ?::uuid", userId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from answers where attempt_id = ?::uuid", Integer.class, student.attemptId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from attempt_scores where attempt_id = ?::uuid", Integer.class, student.attemptId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from student_constraints where attempt_id = ?::uuid", Integer.class, student.attemptId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from recommendations where attempt_id = ?::uuid", Integer.class, student.attemptId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from recommendation_summaries where attempt_id = ?::uuid", Integer.class, student.attemptId())).isZero();
        assertThat(count("select count(*) from chat_messages where user_id = ?::uuid", userId)).isZero();
        assertThat(count("select count(*) from qa_threads where author_id = ?::uuid", userId)).isZero();
        assertThat(count("select count(*) from qa_answers where author_id = ?::uuid", userId)).isZero();
        // family links and consent evidence
        assertThat(jdbc.queryForObject("select count(*) from parent_student_links where student_id = ?::uuid or parent_id = ?::uuid",
                Integer.class, userId, userId)).isZero();
        assertThat(count("select count(*) from parental_consents where student_id = ?::uuid", userId)).isZero();
        assertThat(count("select count(*) from refresh_tokens where user_id = ?::uuid and revoked_at is null", userId)).isZero();

        // the account row is anonymised, not left with personal data
        Map<String, Object> row = jdbc.queryForMap("select email, full_name, date_of_birth, status, password_hash from users where id = ?::uuid", userId);
        assertThat((String) row.get("email")).isEqualTo("deleted-" + userId + "@deleted.invalid");
        assertThat(row.get("full_name")).isNull();
        assertThat(row.get("date_of_birth")).isNull();
        assertThat(row.get("status")).isEqualTo("LOCKED");
        assertThat(row.get("password_hash")).isEqualTo("!");

        // the audit entry keeps only ids
        List<AuditLog> audit = auditLogs.findByEntityTypeAndEntityId("User", UUID.fromString(userId));
        AuditLog deleted = audit.stream().filter(a -> a.getAction().equals("ACCOUNT_DELETED")).findFirst().orElseThrow();
        assertThat(deleted.getActorId()).isEqualTo(UUID.fromString(userId));
        assertThat(deleted.getMetadata()).containsEntry("role", "STUDENT").doesNotContainKey("email");

        // somebody else's data is untouched
        assertThat(count("select count(*) from survey_attempts where user_id = ?::uuid", bystander.userId())).isEqualTo(1);
        assertThat(result(bystander).get("recommendations")).hasSize(5);
    }

    @Test
    void afterDeletionTheOldCredentialsAndTokensStopWorking() throws Exception {
        Student student = readyStudent();
        String refresh = data(login(student.email(), PASSWORD), 200).get("refreshToken").asText();
        data(delete("/api/v1/me", student.token()), 200);

        assertError(login(student.email(), PASSWORD), 401, "INVALID_CREDENTIALS");
        assertError(get("/api/v1/me", student.token()), 401, "UNAUTHORIZED");
        assertError(post("/api/v1/auth/refresh", Map.of("refreshToken", refresh), null), 401, "REFRESH_TOKEN_INVALID");
        // a second delete or export with the still-unexpired access token is refused
        assertError(delete("/api/v1/me", student.token()), 401, "UNAUTHORIZED");
        assertError(get("/api/v1/me/export", student.token()), 401, "UNAUTHORIZED");
        assertThat(auditLogs.findByEntityTypeAndEntityId("User", UUID.fromString(student.userId())).stream()
                .filter(a -> a.getAction().equals("ACCOUNT_DELETED")).count()).isEqualTo(1);
    }

    @Test
    void theSameEmailCanRegisterAgainAfterDeletion() throws Exception {
        Student student = readyStudent();
        data(delete("/api/v1/me", student.token()), 200);

        MvcResult again = register(student.email(), "STUDENT", yearsAgo(17));

        assertThat(again.getResponse().getStatus()).isEqualTo(201);
        String newToken = loginOrFail(student.email(), PASSWORD).get("accessToken").asText();
        assertThat(data(get("/api/v1/me/attempts", newToken), 200).get("totalItems").asInt()).isZero();
    }

    @Test
    void deletingAParentRemovesTheLinkButLeavesTheStudentsAccessAndConsentEvidence() throws Exception {
        Student young = startedStudent(15, null, null);
        JsonNode parent = registerAndLogin("PARENT", null);
        String code = data(post("/api/v1/parent/invites", null, young.token()), 200).get("inviteCode").asText();
        data(post("/api/v1/parent/links", Map.of("inviteCode", code, "consent", true), parent.get("accessToken").asText()), 200);

        data(delete("/api/v1/me", parent.get("accessToken").asText()), 200);

        assertThat(jdbc.queryForObject("select count(*) from parent_student_links where student_id = ?::uuid", Integer.class, young.userId())).isZero();
        assertThat(count("select count(*) from parental_consents where student_id = ?::uuid", young.userId()))
                .as("the student keeps the proof that consent was given").isEqualTo(1);
        assertThat(count("select count(*) from users where id = ?::uuid and status = 'ACTIVE'", young.userId())).isEqualTo(1);
        assertThat(data(get("/api/v1/me", young.token()), 200).get("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void deletingAMentorRemovesTheProfileThePostsAndTheirSearchIndex() throws Exception {
        String admin = adminToken();
        JsonNode session = registerAndLogin("PARENT", null);
        String userId = session.get("user").get("id").asText();
        data(post("/api/v1/mentor/apply", Map.of("jobTitle", "Chuyên gia sắp xóa", "yearsExperience", 3, "company", "Công ty riêng tư",
                "bio", "Tiểu sử riêng tư"), session.get("accessToken").asText()), 201);
        String token = data(post("/api/v1/auth/refresh", Map.of("refreshToken", session.get("refreshToken").asText()), null), 200)
                .get("accessToken").asText();
        data(patch("/api/v1/admin/mentors/" + userId + "/verify", Map.of(), admin), 200);
        Map<String, String> sections = Map.of("a_day_at_work", "a", "wish_i_knew", "b", "dark_side", "c", "who_fits", "d", "school_vs_work", "e");
        String published = data(post("/api/v1/povs", Map.of("majorId", majorId("LAW"), "title", "Bài sẽ bị xóa", "sections", sections), token), 201)
                .get("id").asText();
        String draft = data(post("/api/v1/povs", Map.of("majorId", majorId("LAW"), "title", "Bản nháp sẽ bị xóa", "sections", sections), token), 201)
                .get("id").asText();
        data(post("/api/v1/povs/" + published + "/submit", null, token), 200);
        data(patch("/api/v1/admin/povs/" + published + "/status", Map.of("status", "PUBLISHED"), admin), 200);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(vectorStore.countBySource("POV", UUID.fromString(published))).isGreaterThanOrEqualTo(1));
        assertThat(data(get("/api/v1/povs/" + published, null), 200).get("title").asText()).isEqualTo("Bài sẽ bị xóa");

        data(delete("/api/v1/me", token), 200);

        assertThat(count("select count(*) from mentors where user_id = ?::uuid", userId)).isZero();
        assertThat(count("select count(*) from pov_posts where mentor_id = ?::uuid", userId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from pov_posts where id = ?::uuid", Integer.class, draft)).isZero();
        assertError(get("/api/v1/povs/" + published, null), 404, "POV_NOT_FOUND");
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(vectorStore.countBySource("POV", UUID.fromString(published))).isZero());
    }

    @Test
    void adminAccountsCannotBeDeletedThroughTheApi() throws Exception {
        assertError(delete("/api/v1/me", adminToken()), 422, "ACCOUNT_DELETE_NOT_ALLOWED");
        assertThat(get("/api/v1/me", adminToken()).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void bothEndpointsNeedLogin() throws Exception {
        assertError(delete("/api/v1/me", null), 401, "UNAUTHORIZED");
        assertError(get("/api/v1/me/export", null), 401, "UNAUTHORIZED");
    }

    @Test
    void answersOfADeletedUserInAThreadOfSomeoneElseDisappearButTheThreadStays() throws Exception {
        String major = majorId("PSYCHOLOGY");
        String ownerToken = studentToken();
        String threadId = data(post("/api/v1/majors/" + major + "/qa", Map.of("title", "Chủ đề còn lại " + UUID.randomUUID(),
                "content", "Nội dung"), ownerToken), 201).get("id").asText();
        Student leaver = startedStudent(null, null);
        data(post("/api/v1/qa/" + threadId + "/answers", Map.of("content", "Câu trả lời của người sắp rời đi"), leaver.token()), 201);
        data(post("/api/v1/qa/" + threadId + "/answers", Map.of("content", "Câu trả lời của người ở lại"), ownerToken), 201);

        data(delete("/api/v1/me", leaver.token()), 200);

        JsonNode answers = data(get("/api/v1/qa/" + threadId + "/answers", ownerToken), 200);
        assertThat(answers.get("items")).hasSize(1);
        assertThat(answers.get("items").get(0).get("content").asText()).isEqualTo("Câu trả lời của người ở lại");
        assertThat(StreamSupport.stream(data(get("/api/v1/majors/" + major + "/qa?size=100", ownerToken), 200).get("items").spliterator(), false)
                .anyMatch(t -> t.get("id").asText().equals(threadId))).isTrue();
    }
}

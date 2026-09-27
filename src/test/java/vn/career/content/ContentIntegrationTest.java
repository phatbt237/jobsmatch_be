package vn.career.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import vn.career.common.audit.AuditLog;
import vn.career.common.audit.AuditLogRepository;
import vn.career.support.AbstractIntegrationTest;

class ContentIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AuditLogRepository auditLogs;

    private record Mentor(String userId, String token, String refreshToken) {
    }

    // ---------- helpers ----------

    private JsonNode data(MvcResult result, int status) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        return json(result).get("data");
    }

    private void assertError(MvcResult result, int status, String code) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        assertThat(json(result).get("error").get("code").asText()).isEqualTo(code);
    }

    private String majorId(String code) {
        return jdbc.queryForObject("select id::text from majors where code = ?", String.class, code);
    }

    private static Map<String, String> fullSections(String tag) {
        return Map.of("a_day_at_work", "Một ngày làm việc " + tag, "wish_i_knew", "Điều ước biết " + tag,
                "dark_side", "Mặt trái " + tag, "who_fits", "Ai phù hợp " + tag, "school_vs_work", "Trường và đi làm " + tag);
    }

    private Map<String, Object> povBody(String majorCode, String title, Map<String, String> sections) {
        Map<String, Object> body = new HashMap<>();
        body.put("majorId", majorId(majorCode));
        body.put("title", title);
        body.put("sections", sections);
        return body;
    }

    /** An adult account that applied to be a mentor; its token already carries the MENTOR role. Not verified yet. */
    private Mentor appliedMentor() throws Exception {
        JsonNode session = registerAndLogin("PARENT", null);
        String applyToken = session.get("accessToken").asText();
        data(post("/api/v1/mentor/apply", Map.of("jobTitle", "Kỹ sư thử nghiệm", "yearsExperience", 4, "company", "Công ty thử",
                "bio", "Giới thiệu ngắn", "linkedinUrl", "https://example.com/in/someone"), applyToken), 201);
        JsonNode refreshed = data(post("/api/v1/auth/refresh", Map.of("refreshToken", session.get("refreshToken").asText()), null), 200);
        return new Mentor(session.get("user").get("id").asText(), refreshed.get("accessToken").asText(),
                refreshed.get("refreshToken").asText());
    }

    private Mentor verifiedMentor() throws Exception {
        Mentor mentor = appliedMentor();
        data(patch("/api/v1/admin/mentors/" + mentor.userId() + "/verify", Map.of(), adminToken()), 200);
        return mentor;
    }

    private MvcResult patch(String path, Object body, String token) throws Exception {
        return send(org.springframework.http.HttpMethod.PATCH, path, body, token);
    }

    private static List<String> titles(JsonNode page) {
        return StreamSupport.stream(page.get("items").spliterator(), false).map(i -> i.get("title").asText()).toList();
    }

    // ---------- seed ----------

    @Test
    void fiveSamplePostsArePublishedAndFlaggedAsSample() throws Exception {
        for (String code : List.of("IT", "ACCOUNTING", "MEDICINE", "GRAPHICDESIGN", "MARKETING")) {
            JsonNode page = data(get("/api/v1/majors/" + majorId(code) + "/povs?size=100", null), 200);

            // other tests may publish real posts on the same major, so look for the sample one
            JsonNode post = StreamSupport.stream(page.get("items").spliterator(), false)
                    .filter(p -> p.get("sampleData").asBoolean()).findFirst()
                    .orElseThrow(() -> new AssertionError("no sample post published for " + code));
            assertThat(post.get("sections").size()).isEqualTo(5);
            assertThat(post.get("mentor").get("jobTitle").asText()).isNotBlank();
            assertThat(post.get("mentor").get("yearsExperience").asInt()).isPositive();
        }
    }

    @Test
    void publicPostsRevealNoPersonalDetailsOfTheMentor() throws Exception {
        JsonNode post = StreamSupport.stream(data(get("/api/v1/majors/" + majorId("IT") + "/povs?size=100", null), 200)
                .get("items").spliterator(), false).filter(p -> p.get("sampleData").asBoolean()).findFirst().orElseThrow();

        assertThat(post.get("mentor").fieldNames()).toIterable().containsExactlyInAnyOrder("jobTitle", "yearsExperience");
        String raw = post.toString();
        assertThat(raw).doesNotContain("company", "linkedin", "email", "sample-mentor", "Công ty mẫu");
        // and a single post is readable without a token as well
        assertThat(data(get("/api/v1/povs/" + post.get("id").asText(), null), 200).get("title").asText())
                .isEqualTo(post.get("title").asText());
    }

    @Test
    void unknownMajorsAndPostsAreNotFound() throws Exception {
        assertError(get("/api/v1/majors/" + UUID.randomUUID() + "/povs", null), 404, "MAJOR_NOT_FOUND");
        assertError(get("/api/v1/povs/" + UUID.randomUUID(), null), 404, "POV_NOT_FOUND");
    }

    // ---------- acceptance: mentor writes, admin approves, it shows on the major page ----------

    @Test
    void mentorCreatesSubmitsAdminApprovesAndThePostAppearsOnTheMajorPage() throws Exception {
        String admin = adminToken();
        Mentor mentor = appliedMentor();
        String title = "Bài thử luồng duyệt " + UUID.randomUUID();
        String itMajor = majorId("IT");

        // 1. an unverified mentor cannot write yet
        assertError(post("/api/v1/povs", povBody("IT", title, fullSections("v1")), mentor.token()), 403, "MENTOR_NOT_VERIFIED");

        // 2. admin verifies
        JsonNode verified = data(patch("/api/v1/admin/mentors/" + mentor.userId() + "/verify", Map.of(), admin), 200);
        assertThat(verified.get("verified").asBoolean()).isTrue();

        // 3. the mentor creates a draft; a draft is invisible to the public
        JsonNode draft = data(post("/api/v1/povs", povBody("IT", title, fullSections("v1")), mentor.token()), 201);
        String postId = draft.get("id").asText();
        assertThat(draft.get("status").asText()).isEqualTo("DRAFT");
        assertError(get("/api/v1/povs/" + postId, null), 404, "POV_NOT_FOUND");
        assertThat(titles(data(get("/api/v1/majors/" + itMajor + "/povs?size=100", null), 200))).doesNotContain(title);

        // 4. submit for review
        JsonNode pending = data(post("/api/v1/povs/" + postId + "/submit", null, mentor.token()), 200);
        assertThat(pending.get("status").asText()).isEqualTo("PENDING_REVIEW");
        assertError(get("/api/v1/povs/" + postId, null), 404, "POV_NOT_FOUND");
        // no editing while it waits
        assertError(put("/api/v1/povs/" + postId, povBody("IT", "đổi tiêu đề", fullSections("v2")), mentor.token()),
                422, "POV_NOT_EDITABLE");

        // 5. it shows up in the admin review queue
        JsonNode queue = data(get("/api/v1/admin/povs?status=PENDING_REVIEW&size=100", admin), 200);
        assertThat(titles(queue)).contains(title);

        // 6. admin approves
        JsonNode published = data(patch("/api/v1/admin/povs/" + postId + "/status", Map.of("status", "PUBLISHED"), admin), 200);
        assertThat(published.get("status").asText()).isEqualTo("PUBLISHED");
        assertThat(published.get("publishedAt").asText()).isNotBlank();

        // 7. now everybody sees it on the major page and by id
        JsonNode list = data(get("/api/v1/majors/" + itMajor + "/povs?size=100", null), 200);
        assertThat(titles(list)).contains(title);
        JsonNode single = data(get("/api/v1/povs/" + postId, null), 200);
        assertThat(single.get("sampleData").asBoolean()).isFalse();
        assertThat(single.get("mentor").get("jobTitle").asText()).isEqualTo("Kỹ sư thử nghiệm");
        assertThat(single.get("mentor").get("yearsExperience").asInt()).isEqualTo(4);
        assertThat(single.get("sections").get("who_fits").asText()).isEqualTo("Ai phù hợp v1");

        // 8. the approval was audited, and a published post is frozen
        List<AuditLog> audit = auditLogs.findByEntityTypeAndEntityId("PovPost", UUID.fromString(postId));
        assertThat(audit).extracting(AuditLog::getAction).containsExactly("POV_REVIEWED");
        assertThat(audit.get(0).getMetadata()).containsEntry("decision", "PUBLISHED");
        assertError(put("/api/v1/povs/" + postId, povBody("IT", "sửa sau khi đăng", fullSections("v3")), mentor.token()),
                422, "POV_NOT_EDITABLE");
        assertError(patch("/api/v1/admin/povs/" + postId + "/status", Map.of("status", "PUBLISHED"), admin), 422, "POV_INVALID_STATE");
    }

    @Test
    void aRejectedPostCanBeFixedAndSentAgain() throws Exception {
        String admin = adminToken();
        Mentor mentor = verifiedMentor();
        String postId = data(post("/api/v1/povs", povBody("ACCOUNTING", "Bài bị từ chối", fullSections("a")), mentor.token()), 201)
                .get("id").asText();
        data(post("/api/v1/povs/" + postId + "/submit", null, mentor.token()), 200);

        // a rejection needs a reason
        assertError(patch("/api/v1/admin/povs/" + postId + "/status", Map.of("status", "REJECTED"), admin), 400, "VALIDATION_ERROR");
        assertError(patch("/api/v1/admin/povs/" + postId + "/status", Map.of("status", "REJECTED", "reason", "  "), admin),
                400, "VALIDATION_ERROR");
        assertError(patch("/api/v1/admin/povs/" + postId + "/status", Map.of("status", "DRAFT"), admin), 400, "VALIDATION_ERROR");
        JsonNode rejected = data(patch("/api/v1/admin/povs/" + postId + "/status",
                Map.of("status", "REJECTED", "reason", "Hãy bổ sung ví dụ cụ thể hơn"), admin), 200);
        assertThat(rejected.get("status").asText()).isEqualTo("REJECTED");

        // the mentor sees the reason
        JsonNode mine = data(get("/api/v1/mentor/povs?size=100", mentor.token()), 200);
        JsonNode myPost = StreamSupport.stream(mine.get("items").spliterator(), false)
                .filter(p -> p.get("id").asText().equals(postId)).findFirst().orElseThrow();
        assertThat(myPost.get("status").asText()).isEqualTo("REJECTED");
        assertThat(myPost.get("rejectReason").asText()).isEqualTo("Hãy bổ sung ví dụ cụ thể hơn");
        assertError(get("/api/v1/povs/" + postId, null), 404, "POV_NOT_FOUND");

        // fix it and send it again: the old reason disappears
        JsonNode edited = data(put("/api/v1/povs/" + postId, povBody("ACCOUNTING", "Bài đã sửa", fullSections("b")), mentor.token()), 200);
        assertThat(edited.get("status").asText()).isEqualTo("REJECTED");
        JsonNode again = data(post("/api/v1/povs/" + postId + "/submit", null, mentor.token()), 200);
        assertThat(again.get("status").asText()).isEqualTo("PENDING_REVIEW");
        assertThat(again.get("rejectReason").isNull()).isTrue();
        data(patch("/api/v1/admin/povs/" + postId + "/status", Map.of("status", "PUBLISHED"), admin), 200);
        assertThat(data(get("/api/v1/povs/" + postId, null), 200).get("title").asText()).isEqualTo("Bài đã sửa");
        assertThat(auditLogs.findByEntityTypeAndEntityId("PovPost", UUID.fromString(postId))).hasSize(2);
    }

    @Test
    void aPostWithMissingSectionsCannotBeSubmittedButCanBeSavedAsADraft() throws Exception {
        Mentor mentor = verifiedMentor();
        JsonNode draft = data(post("/api/v1/povs", povBody("LAW", "Bản nháp dở dang",
                Map.of("a_day_at_work", "  Chỉ có một phần  ", "dark_side", "   ")), mentor.token()), 201);
        String id = draft.get("id").asText();

        // blank sections are dropped, text is trimmed
        assertThat(draft.get("sections").size()).isEqualTo(1);
        assertThat(draft.get("sections").get("a_day_at_work").asText()).isEqualTo("Chỉ có một phần");

        MvcResult result = post("/api/v1/povs/" + id + "/submit", null, mentor.token());
        assertError(result, 400, "VALIDATION_ERROR");
        assertThat(json(result).get("error").get("details").size()).isEqualTo(4);
        assertThat(jdbc.queryForObject("select status from pov_posts where id = ?::uuid", String.class, id)).isEqualTo("DRAFT");
    }

    // ---------- validation ----------

    @Test
    void sectionKeysAndLengthsAreValidated() throws Exception {
        Mentor mentor = verifiedMentor();

        assertError(post("/api/v1/povs", povBody("IT", "x", Map.of("unknown_part", "text")), mentor.token()), 400, "VALIDATION_ERROR");
        assertError(post("/api/v1/povs", povBody("IT", "x", Map.of("who_fits", "a".repeat(5001))), mentor.token()), 400, "VALIDATION_ERROR");
        assertError(post("/api/v1/povs", povBody("IT", " ", fullSections("x")), mentor.token()), 400, "VALIDATION_ERROR");
        Map<String, Object> badVideo = povBody("IT", "x", fullSections("x"));
        badVideo.put("videoUrl", "http://insecure.example.com/video");
        assertError(post("/api/v1/povs", badVideo, mentor.token()), 400, "VALIDATION_ERROR");
        Map<String, Object> unknownMajor = povBody("IT", "x", fullSections("x"));
        unknownMajor.put("majorId", UUID.randomUUID().toString());
        assertError(post("/api/v1/povs", unknownMajor, mentor.token()), 404, "MAJOR_NOT_FOUND");
    }

    // ---------- access control ----------

    @Test
    void onlyTheAuthorCanEditOrSubmitAPost() throws Exception {
        Mentor owner = verifiedMentor();
        Mentor other = verifiedMentor();
        String postId = data(post("/api/v1/povs", povBody("IT", "Bài của owner", fullSections("o")), owner.token()), 201)
                .get("id").asText();

        assertError(put("/api/v1/povs/" + postId, povBody("IT", "chiếm bài", fullSections("x")), other.token()), 403, "ACCESS_DENIED");
        assertError(post("/api/v1/povs/" + postId + "/submit", null, other.token()), 403, "ACCESS_DENIED");
        assertError(put("/api/v1/povs/" + UUID.randomUUID(), povBody("IT", "x", fullSections("x")), owner.token()), 404, "POV_NOT_FOUND");
        assertThat(jdbc.queryForObject("select title from pov_posts where id = ?::uuid", String.class, postId)).isEqualTo("Bài của owner");
    }

    @Test
    void studentsParentsAndAnonymousUsersCannotWritePosts() throws Exception {
        Map<String, Object> body = povBody("IT", "x", fullSections("x"));

        assertError(post("/api/v1/povs", body, studentToken()), 403, "ACCESS_DENIED");
        assertError(post("/api/v1/povs", body, registerAndLogin("PARENT", null).get("accessToken").asText()), 403, "ACCESS_DENIED");
        assertError(post("/api/v1/povs", body, adminToken()), 403, "ACCESS_DENIED");
        assertError(post("/api/v1/povs", body, null), 401, "UNAUTHORIZED");
    }

    @Test
    void reviewEndpointsAreAdminOnly() throws Exception {
        Mentor mentor = verifiedMentor();
        String postId = data(post("/api/v1/povs", povBody("IT", "Chờ duyệt", fullSections("x")), mentor.token()), 201).get("id").asText();
        data(post("/api/v1/povs/" + postId + "/submit", null, mentor.token()), 200);

        for (String token : List.of(mentor.token(), studentToken())) {
            assertError(patch("/api/v1/admin/povs/" + postId + "/status", Map.of("status", "PUBLISHED"), token), 403, "ACCESS_DENIED");
            assertError(get("/api/v1/admin/povs", token), 403, "ACCESS_DENIED");
            assertError(patch("/api/v1/admin/mentors/" + mentor.userId() + "/verify", Map.of(), token), 403, "ACCESS_DENIED");
            assertError(get("/api/v1/admin/mentors", token), 403, "ACCESS_DENIED");
        }
        assertThat(jdbc.queryForObject("select status from pov_posts where id = ?::uuid", String.class, postId)).isEqualTo("PENDING_REVIEW");
    }

    @Test
    void aPostThatIsNotWaitingForReviewCannotBeReviewed() throws Exception {
        Mentor mentor = verifiedMentor();
        String postId = data(post("/api/v1/povs", povBody("IT", "Vẫn là bản nháp", fullSections("x")), mentor.token()), 201).get("id").asText();

        assertError(patch("/api/v1/admin/povs/" + postId + "/status", Map.of("status", "PUBLISHED"), adminToken()), 422, "POV_INVALID_STATE");
        assertError(patch("/api/v1/admin/povs/" + UUID.randomUUID() + "/status", Map.of("status", "PUBLISHED"), adminToken()),
                404, "POV_NOT_FOUND");
    }

    // ---------- mentor onboarding ----------

    @Test
    void applyingMakesTheAccountAMentorAndIsAudited() throws Exception {
        JsonNode session = registerAndLogin("PARENT", null);
        String userId = session.get("user").get("id").asText();

        JsonNode mentor = data(post("/api/v1/mentor/apply", Map.of("jobTitle", "Bác sĩ", "yearsExperience", 12,
                "majorId", majorId("MEDICINE")), session.get("accessToken").asText()), 201);

        assertThat(mentor.get("verified").asBoolean()).isFalse();
        assertThat(mentor.get("jobTitle").asText()).isEqualTo("Bác sĩ");
        assertThat(jdbc.queryForObject("select role from users where id = ?::uuid", String.class, userId)).isEqualTo("MENTOR");
        List<AuditLog> audit = auditLogs.findByEntityTypeAndEntityId("User", UUID.fromString(userId));
        assertThat(audit).extracting(AuditLog::getAction).contains("ROLE_CHANGED");
        assertThat(audit.get(0).getMetadata()).containsEntry("from", "PARENT").containsEntry("to", "MENTOR");
        // after a refresh the new token carries the MENTOR role and reaches mentor-only endpoints
        String newToken = data(post("/api/v1/auth/refresh", Map.of("refreshToken", session.get("refreshToken").asText()), null), 200)
                .get("accessToken").asText();
        assertThat(data(get("/api/v1/mentor/me", newToken), 200).get("verified").asBoolean()).isFalse();
        assertError(get("/api/v1/mentor/me", session.get("accessToken").asText()), 403, "ACCESS_DENIED");   // old token, old role
    }

    @Test
    void onlyAdultAccountsCanApplyAndOnlyOnce() throws Exception {
        assertError(post("/api/v1/mentor/apply", Map.of("jobTitle", "x", "yearsExperience", 1), studentToken()), 403, "ACCESS_DENIED");
        assertError(post("/api/v1/mentor/apply", Map.of("jobTitle", "x", "yearsExperience", 1), adminToken()), 403, "ACCESS_DENIED");
        assertError(post("/api/v1/mentor/apply", Map.of("jobTitle", "x", "yearsExperience", 1), null), 401, "UNAUTHORIZED");

        JsonNode session = registerAndLogin("PARENT", null);
        String token = session.get("accessToken").asText();
        Map<String, Object> ok = Map.of("jobTitle", "Kỹ sư", "yearsExperience", 3);
        data(post("/api/v1/mentor/apply", ok, token), 201);
        assertError(post("/api/v1/mentor/apply", ok, token), 422, "MENTOR_ALREADY_APPLIED");
    }

    @Test
    void applicationDetailsAreValidated() throws Exception {
        String token = registerAndLogin("PARENT", null).get("accessToken").asText();

        assertError(post("/api/v1/mentor/apply", Map.of("jobTitle", "", "yearsExperience", 3), token), 400, "VALIDATION_ERROR");
        assertError(post("/api/v1/mentor/apply", Map.of("jobTitle", "x", "yearsExperience", -1), token), 400, "VALIDATION_ERROR");
        assertError(post("/api/v1/mentor/apply", Map.of("jobTitle", "x", "yearsExperience", 61), token), 400, "VALIDATION_ERROR");
        assertError(post("/api/v1/mentor/apply", Map.of("jobTitle", "x", "yearsExperience", 3, "linkedinUrl", "http://x"), token),
                400, "VALIDATION_ERROR");
        assertError(post("/api/v1/mentor/apply", Map.of("jobTitle", "x", "yearsExperience", 3, "majorId", UUID.randomUUID().toString()),
                token), 404, "MAJOR_NOT_FOUND");
        // none of the failed attempts changed the role
        assertThat(get("/api/v1/me", token).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void adminsCanListApplicantsVerifyAndWithdrawVerification() throws Exception {
        String admin = adminToken();
        Mentor mentor = appliedMentor();

        JsonNode unverified = data(get("/api/v1/admin/mentors?verified=false&size=100", admin), 200);
        assertThat(StreamSupport.stream(unverified.get("items").spliterator(), false)
                .anyMatch(m -> m.get("userId").asText().equals(mentor.userId()))).isTrue();

        data(patch("/api/v1/admin/mentors/" + mentor.userId() + "/verify", Map.of("verified", true), admin), 200);
        data(post("/api/v1/povs", povBody("IT", "Sau khi xác minh " + UUID.randomUUID(), fullSections("x")), mentor.token()), 201);

        // withdrawing the verification blocks new posts again
        JsonNode withdrawn = data(patch("/api/v1/admin/mentors/" + mentor.userId() + "/verify", Map.of("verified", false), admin), 200);
        assertThat(withdrawn.get("verified").asBoolean()).isFalse();
        assertError(post("/api/v1/povs", povBody("IT", "Sau khi bị gỡ xác minh", fullSections("x")), mentor.token()), 403, "MENTOR_NOT_VERIFIED");
        assertThat(auditLogs.findByEntityTypeAndEntityId("Mentor", UUID.fromString(mentor.userId())))
                .extracting(AuditLog::getAction).containsExactly("MENTOR_VERIFIED", "MENTOR_VERIFIED");
        assertError(patch("/api/v1/admin/mentors/" + UUID.randomUUID() + "/verify", Map.of(), admin), 404, "MENTOR_NOT_FOUND");
    }

    // ---------- Q&A ----------

    private Map<String, Object> question(String title) {
        return Map.of("title", title, "content", "Nội dung câu hỏi của " + title);
    }

    @Test
    void studentsAndMentorsTakePartInCommunityQaWithoutRevealingNames() throws Exception {
        String major = majorId("IT");
        JsonNode studentSession = registerAndLogin("STUDENT", yearsAgo(17));
        String student = studentSession.get("accessToken").asText();
        Mentor mentor = verifiedMentor();

        JsonNode thread = data(post("/api/v1/majors/" + major + "/qa", question("Học CNTT cần giỏi toán không?"), student), 201);
        String threadId = thread.get("id").asText();
        assertThat(thread.get("authorLabel").asText()).isEqualTo("Học sinh");
        assertThat(thread.get("authorRole").asText()).isEqualTo("STUDENT");
        assertThat(thread.toString()).doesNotContain("Test STUDENT").doesNotContain(studentSession.get("user").get("email").asText());

        data(post("/api/v1/qa/" + threadId + "/answers", Map.of("content", "Mình cũng đang thắc mắc"), student), 201);
        JsonNode mentorAnswer = data(post("/api/v1/qa/" + threadId + "/answers", Map.of("content", "Cần tư duy logic hơn là toán cao cấp"),
                mentor.token()), 201);
        assertThat(mentorAnswer.get("mentorAnswer").asBoolean()).isTrue();
        assertThat(mentorAnswer.get("authorLabel").asText()).isEqualTo("Kỹ sư thử nghiệm (mentor)");

        JsonNode answers = data(get("/api/v1/qa/" + threadId + "/answers", student), 200);
        assertThat(answers.get("items")).hasSize(2);
        assertThat(answers.get("items").get(0).get("mentorAnswer").asBoolean()).isFalse();
        assertThat(answers.get("items").get(0).get("authorLabel").asText()).isEqualTo("Học sinh");
        assertThat(answers.get("items").get(1).get("mentorAnswer").asBoolean()).isTrue();

        JsonNode threads = data(get("/api/v1/majors/" + major + "/qa", student), 200);
        JsonNode listed = StreamSupport.stream(threads.get("items").spliterator(), false)
                .filter(t -> t.get("id").asText().equals(threadId)).findFirst().orElseThrow();
        assertThat(listed.get("answerCount").asLong()).isEqualTo(2);
    }

    @Test
    void anUnverifiedMentorsAnswerIsNotFlagged() throws Exception {
        String threadId = data(post("/api/v1/majors/" + majorId("LAW") + "/qa", question("Hỏi ngành luật"), studentToken()), 201)
                .get("id").asText();
        Mentor unverified = appliedMentor();

        JsonNode answer = data(post("/api/v1/qa/" + threadId + "/answers", Map.of("content", "Trả lời thử"), unverified.token()), 201);

        assertThat(answer.get("mentorAnswer").asBoolean()).isFalse();
    }

    @Test
    void studentsWaitingForParentalConsentAreBlockedUntilAParentApproves() throws Exception {
        String major = majorId("IT");
        String existingThread = data(post("/api/v1/majors/" + major + "/qa", question("Câu hỏi có sẵn"), studentToken()), 201).get("id").asText();
        JsonNode young = registerAndLogin("STUDENT", yearsAgo(15));
        String token = young.get("accessToken").asText();

        assertError(get("/api/v1/majors/" + major + "/qa", token), 403, "CONSENT_REQUIRED");
        assertError(post("/api/v1/majors/" + major + "/qa", question("Em hỏi"), token), 403, "CONSENT_REQUIRED");
        assertError(get("/api/v1/qa/" + existingThread + "/answers", token), 403, "CONSENT_REQUIRED");
        assertError(post("/api/v1/qa/" + existingThread + "/answers", Map.of("content", "x"), token), 403, "CONSENT_REQUIRED");

        // a parent approves through the invite flow
        String parent = registerAndLogin("PARENT", null).get("accessToken").asText();
        String code = data(post("/api/v1/parent/invites", null, token), 200).get("inviteCode").asText();
        data(post("/api/v1/parent/links", Map.of("inviteCode", code, "consent", true), parent), 200);

        assertThat(data(get("/api/v1/majors/" + major + "/qa", token), 200).get("items")).isNotEmpty();
        data(post("/api/v1/majors/" + major + "/qa", question("Em hỏi sau khi được đồng ý"), token), 201);
    }

    @Test
    void qaNeedsLoginAndValidInput() throws Exception {
        String major = majorId("IT");
        String student = studentToken();

        assertError(get("/api/v1/majors/" + major + "/qa", null), 401, "UNAUTHORIZED");
        assertError(post("/api/v1/majors/" + major + "/qa", question("x"), null), 401, "UNAUTHORIZED");
        assertError(post("/api/v1/majors/" + major + "/qa", Map.of("title", " ", "content", "x"), student), 400, "VALIDATION_ERROR");
        assertError(post("/api/v1/majors/" + major + "/qa", Map.of("title", "x", "content", "a".repeat(5001)), student), 400, "VALIDATION_ERROR");
        assertError(post("/api/v1/majors/" + UUID.randomUUID() + "/qa", question("x"), student), 404, "MAJOR_NOT_FOUND");
        assertError(get("/api/v1/qa/" + UUID.randomUUID() + "/answers", student), 404, "THREAD_NOT_FOUND");
        assertError(post("/api/v1/qa/" + UUID.randomUUID() + "/answers", Map.of("content", "x"), student), 404, "THREAD_NOT_FOUND");
        String threadId = data(post("/api/v1/majors/" + major + "/qa", question("Hợp lệ"), student), 201).get("id").asText();
        assertError(post("/api/v1/qa/" + threadId + "/answers", Map.of("content", ""), student), 400, "VALIDATION_ERROR");
    }

    @Test
    void adminsCanHideThreadsAndAnswers() throws Exception {
        String admin = adminToken();
        String major = majorId("MARKETING");
        String student = studentToken();
        String threadId = data(post("/api/v1/majors/" + major + "/qa", question("Câu hỏi sẽ bị ẩn " + UUID.randomUUID()), student), 201)
                .get("id").asText();
        String answerId = data(post("/api/v1/qa/" + threadId + "/answers", Map.of("content", "Câu trả lời sẽ bị ẩn"), student), 201)
                .get("id").asText();
        String keptAnswer = data(post("/api/v1/qa/" + threadId + "/answers", Map.of("content", "Câu trả lời giữ lại"), student), 201)
                .get("id").asText();

        assertError(patch("/api/v1/admin/qa/answers/" + answerId, Map.of("status", "HIDDEN"), student), 403, "ACCESS_DENIED");
        data(patch("/api/v1/admin/qa/answers/" + answerId, Map.of("status", "HIDDEN"), admin), 200);
        JsonNode answers = data(get("/api/v1/qa/" + threadId + "/answers", student), 200);
        assertThat(answers.get("items")).hasSize(1);
        assertThat(answers.get("items").get(0).get("id").asText()).isEqualTo(keptAnswer);
        assertThat(auditLogs.findByEntityTypeAndEntityId("QaAnswer", UUID.fromString(answerId)))
                .extracting(AuditLog::getAction).containsExactly("QA_MODERATED");

        data(patch("/api/v1/admin/qa/threads/" + threadId, Map.of("status", "HIDDEN"), admin), 200);
        assertError(get("/api/v1/qa/" + threadId + "/answers", student), 404, "THREAD_NOT_FOUND");
        assertError(post("/api/v1/qa/" + threadId + "/answers", Map.of("content", "x"), student), 404, "THREAD_NOT_FOUND");
        assertThat(StreamSupport.stream(data(get("/api/v1/majors/" + major + "/qa?size=100", student), 200).get("items").spliterator(), false)
                .anyMatch(t -> t.get("id").asText().equals(threadId))).isFalse();

        // and it can be shown again
        data(patch("/api/v1/admin/qa/threads/" + threadId, Map.of("status", "VISIBLE"), admin), 200);
        assertThat(data(get("/api/v1/qa/" + threadId + "/answers", student), 200).get("items")).hasSize(1);
        assertError(patch("/api/v1/admin/qa/threads/" + UUID.randomUUID(), Map.of("status", "HIDDEN"), admin), 404, "THREAD_NOT_FOUND");
        assertError(patch("/api/v1/admin/qa/answers/" + UUID.randomUUID(), Map.of("status", "HIDDEN"), admin), 404, "ANSWER_NOT_FOUND");
    }

    @Test
    void threadsArePagedNewestFirst() throws Exception {
        String major = majorId("PSYCHOLOGY");
        String student = studentToken();
        String first = "Câu hỏi đầu " + UUID.randomUUID();
        String second = "Câu hỏi sau " + UUID.randomUUID();
        data(post("/api/v1/majors/" + major + "/qa", question(first), student), 201);
        data(post("/api/v1/majors/" + major + "/qa", question(second), student), 201);

        JsonNode page = data(get("/api/v1/majors/" + major + "/qa?size=1", student), 200);

        assertThat(page.get("items")).hasSize(1);
        assertThat(page.get("items").get(0).get("title").asText()).isEqualTo(second);
        assertThat(page.get("totalItems").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(data(get("/api/v1/majors/" + major + "/qa?size=1&page=1", student), 200).get("items").get(0).get("title").asText())
                .isEqualTo(first);
    }
}

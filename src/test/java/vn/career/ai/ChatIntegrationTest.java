package vn.career.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import vn.career.ai.infrastructure.VectorStore;
import vn.career.common.audit.AuditLogRepository;
import vn.career.support.AbstractIntegrationTest;

/** RAG chatbot: indexing, retrieval with sources, safety, consent, rate limit. */
class ChatIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private VectorStore store;
    @Autowired
    private AuditLogRepository auditLogs;

    @BeforeEach
    void makeSureTheIndexIsBuilt() throws Exception {
        if (store.count() == 0) {
            data(post("/api/v1/admin/embeddings/rebuild", null, adminToken()), 200);
        }
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

    private JsonNode chat(String token, String message, String majorId, String conversationId) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("message", message);
        body.put("majorId", majorId);
        body.put("conversationId", conversationId);
        return data(post("/api/v1/chat", body, token), 200);
    }

    private static List<String> sourceTypes(JsonNode response) {
        return StreamSupport.stream(response.get("sources").spliterator(), false).map(s -> s.get("type").asText()).toList();
    }

    private String seededPovId(String majorCode) {
        return jdbc.queryForObject("select id::text from pov_posts where is_sample and major_id = ?::uuid",
                String.class, majorId(majorCode));
    }

    // ---------- index ----------

    @Test
    void rebuildIndexesEveryMajorAndPublishedPostAndCanBeRepeated() throws Exception {
        String admin = adminToken();

        JsonNode first = data(post("/api/v1/admin/embeddings/rebuild", null, admin), 200);
        int chunksAfterFirst = store.count();
        JsonNode second = data(post("/api/v1/admin/embeddings/rebuild", null, admin), 200);

        assertThat(first.get("majors").asInt()).isGreaterThanOrEqualTo(30);
        assertThat(first.get("povs").asInt()).isGreaterThanOrEqualTo(5);
        assertThat(first.get("chunks").asInt()).isGreaterThanOrEqualTo(35);
        assertThat(first.get("failed").asInt()).isZero();
        assertThat(store.count()).as("rebuilding twice does not duplicate chunks").isEqualTo(chunksAfterFirst);
        assertThat(second.get("chunks").asInt()).isEqualTo(first.get("chunks").asInt());
        assertThat(jdbc.queryForObject("select count(*) from content_chunks where vector_dims(embedding) <> 1536", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(distinct source_id) from content_chunks where source_type = 'MAJOR'", Integer.class))
                .isGreaterThanOrEqualTo(30);
        assertThat(auditLogs.findAll().stream().filter(a -> a.getAction().equals("EMBEDDINGS_REBUILT")).count()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void onlyAdminsCanRebuild() throws Exception {
        assertError(post("/api/v1/admin/embeddings/rebuild", null, studentToken()), 403, "ACCESS_DENIED");
        assertError(post("/api/v1/admin/embeddings/rebuild", null, null), 401, "UNAUTHORIZED");
    }

    // ---------- answers with sources ----------

    @Test
    void aQuestionAboutAMajorWithAMentorPostIsAnsweredFromThePostAndCitesIt() throws Exception {
        String token = studentToken();

        JsonNode answer = chat(token, "Nghề kỹ sư phần mềm có áp lực không?", majorId("IT"), null);

        assertThat(answer.get("answer").asText()).contains("Theo chia sẻ của một Kỹ sư phần mềm có 6 năm kinh nghiệm");
        assertThat(sourceTypes(answer)).contains("POV");
        JsonNode pov = StreamSupport.stream(answer.get("sources").spliterator(), false)
                .filter(s -> s.get("type").asText().equals("POV")).findFirst().orElseThrow();
        assertThat(pov.get("id").asText()).isEqualTo(seededPovId("IT"));
        assertThat(pov.get("title").asText()).isEqualTo("Một ngày làm kỹ sư phần mềm (bài mẫu)");
        assertThat(answer.get("conversationId").asText()).isNotBlank();
    }

    @Test
    void withoutAMajorFilterTheBotSearchesEverything() throws Exception {
        JsonNode answer = chat(studentToken(), "Nghề kế toán tổng hợp thực tế làm gì mỗi ngày?", null, null);

        assertThat(sourceTypes(answer)).isNotEmpty();
        assertThat(StreamSupport.stream(answer.get("sources").spliterator(), false).map(s -> s.get("title").asText()).toList())
                .anyMatch(title -> title.toLowerCase().contains("kế toán"));
        assertThat(answer.get("answer").asText()).isNotBlank();
    }

    @Test
    void theMajorFilterKeepsTheAnswerAboutThatMajor() throws Exception {
        JsonNode answer = chat(studentToken(), "Ngành này có áp lực không?", majorId("MEDICINE"), null);

        for (JsonNode source : answer.get("sources")) {
            assertThat(source.get("title").asText().toLowerCase()).matches(".*(y đa khoa|bác sĩ).*");
        }
    }

    @Test
    void aMajorWithoutAnyIndexedDataGetsAnHonestNoDataAnswerWithoutSources() throws Exception {
        String major = majorId("SOCIALWORK");
        jdbc.update("delete from content_chunks where metadata ->> 'majorId' = ?", major);
        try {
            JsonNode answer = chat(studentToken(), "Ngành này học những gì?", major, null);

            assertThat(answer.get("answer").asText()).contains("chưa có dữ liệu");
            assertThat(answer.get("sources")).isEmpty();
        } finally {
            data(post("/api/v1/admin/embeddings/rebuild", null, adminToken()), 200);
        }
    }

    // ---------- scope and safety ----------

    @Test
    void offTopicQuestionsAreRefusedPolitely() throws Exception {
        JsonNode answer = chat(studentToken(), "Cho mình công thức nấu phở bò", null, null);

        assertThat(answer.get("answer").asText()).contains("chỉ hỗ trợ các câu hỏi về hướng nghiệp");
        assertThat(answer.get("sources")).isEmpty();
    }

    @Test
    void signsOfDistressGetACaringReplyPointingToTrustedPeopleAndHelp() throws Exception {
        String token = studentToken();

        JsonNode answer = chat(token, "Em thấy áp lực quá, em muốn chết", majorId("IT"), null);

        assertThat(answer.get("answer").asText()).contains("Bạn không phải một mình").contains("thầy cô")
                .contains("chuyên gia tâm lý").contains("115");
        assertThat(answer.get("sources")).isEmpty();
        // the exchange is kept in the history like any other
        assertThat(jdbc.queryForObject("select count(*) from chat_messages where conversation_id = ?::uuid", Integer.class,
                answer.get("conversationId").asText())).isEqualTo(2);
        // an ordinary sentence containing "từ từ" is not mistaken for distress
        assertThat(chat(token, "Em nên học ngành công nghệ từ từ hay học nhanh?", majorId("IT"), null).get("answer").asText())
                .doesNotContain("Bạn không phải một mình");
    }

    // ---------- conversations ----------

    @Test
    void messagesOfAConversationAreStoredInOrderAndCanBeContinued() throws Exception {
        String token = studentToken();

        JsonNode first = chat(token, "Ngành công nghệ thông tin học những gì?", majorId("IT"), null);
        String conversation = first.get("conversationId").asText();
        JsonNode second = chat(token, "Còn cơ hội việc làm thì sao?", majorId("IT"), conversation);

        assertThat(second.get("conversationId").asText()).isEqualTo(conversation);
        List<Map<String, Object>> rows = jdbc.queryForList("select role, content from chat_messages where conversation_id = ?::uuid "
                + "order by seq", conversation);
        assertThat(rows).extracting(r -> r.get("role")).containsExactly("USER", "ASSISTANT", "USER", "ASSISTANT");
        assertThat(rows.get(0).get("content")).isEqualTo("Ngành công nghệ thông tin học những gì?");
        assertThat(rows.get(2).get("content")).isEqualTo("Còn cơ hội việc làm thì sao?");
        assertThat(rows.get(1).get("content")).isEqualTo(first.get("answer").asText());
        // the assistant message remembers its sources
        assertThat(jdbc.queryForObject("select sources::text from chat_messages where conversation_id = ?::uuid and role = 'ASSISTANT' "
                + "order by seq limit 1", String.class, conversation)).contains("POV");
    }

    @Test
    void aConversationBelongsToItsOwner() throws Exception {
        JsonNode mine = chat(studentToken(), "Ngành công nghệ thông tin học gì?", null, null);

        assertError(post("/api/v1/chat", Map.of("message", "Ngành nào tốt?", "conversationId", mine.get("conversationId").asText()),
                studentToken()), 404, "CONVERSATION_NOT_FOUND");
        assertError(post("/api/v1/chat", Map.of("message", "Ngành nào tốt?", "conversationId", UUID.randomUUID().toString()),
                studentToken()), 404, "CONVERSATION_NOT_FOUND");
    }

    // ---------- who may chat ----------

    @Test
    void studentsWaitingForParentalConsentCannotChatUntilAParentApproves() throws Exception {
        JsonNode young = registerAndLogin("STUDENT", yearsAgo(15));
        String token = young.get("accessToken").asText();

        assertError(post("/api/v1/chat", Map.of("message", "Ngành công nghệ thông tin học gì?"), token), 403, "CONSENT_REQUIRED");
        assertThat(jdbc.queryForObject("select count(*) from chat_messages where user_id = ?::uuid", Integer.class,
                young.get("user").get("id").asText())).isZero();

        String parent = registerAndLogin("PARENT", null).get("accessToken").asText();
        String code = data(post("/api/v1/parent/invites", null, token), 200).get("inviteCode").asText();
        data(post("/api/v1/parent/links", Map.of("inviteCode", code, "consent", true), parent), 200);

        assertThat(chat(token, "Ngành công nghệ thông tin học gì?", null, null).get("answer").asText()).isNotBlank();
    }

    @Test
    void onlyStudentsCanUseTheChatbot() throws Exception {
        Map<String, Object> body = Map.of("message", "Ngành công nghệ thông tin học gì?");

        assertError(post("/api/v1/chat", body, registerAndLogin("PARENT", null).get("accessToken").asText()), 403, "ACCESS_DENIED");
        assertError(post("/api/v1/chat", body, adminToken()), 403, "ACCESS_DENIED");
        assertError(post("/api/v1/chat", body, null), 401, "UNAUTHORIZED");
    }

    @Test
    void inputIsValidated() throws Exception {
        String token = studentToken();

        assertError(post("/api/v1/chat", Map.of("message", "   "), token), 400, "VALIDATION_ERROR");
        assertError(post("/api/v1/chat", Map.of("message", "a".repeat(2001)), token), 400, "VALIDATION_ERROR");
        assertError(post("/api/v1/chat", Map.of("message", "Ngành gì?", "majorId", UUID.randomUUID().toString()), token),
                404, "MAJOR_NOT_FOUND");
        assertError(post("/api/v1/chat", Map.of("wrong", "field"), token), 400, "VALIDATION_ERROR");
    }

    // ---------- rate limit ----------

    @Test
    void afterTwentyMessagesAnHourTheStudentGetsA429WithRetryAfter() throws Exception {
        String token = studentToken();
        for (int i = 1; i <= 20; i++) {
            assertThat(post("/api/v1/chat", Map.of("message", "Ngành công nghệ thông tin học gì? lần " + i), token).getResponse().getStatus())
                    .as("message %d", i).isEqualTo(200);
        }

        MvcResult limited = post("/api/v1/chat", Map.of("message", "Ngành công nghệ thông tin học gì? lần 21"), token);

        assertError(limited, 429, "TOO_MANY_REQUESTS");
        long retryAfter = Long.parseLong(limited.getResponse().getHeader("Retry-After"));
        assertThat(retryAfter).isBetween(1L, 3600L);
        // nothing was stored for the rejected message
        assertThat(jdbc.queryForObject("select count(*) from chat_messages where content like '%lần 21'", Integer.class)).isZero();
        // the limit is per student
        assertThat(post("/api/v1/chat", Map.of("message", "Ngành công nghệ thông tin học gì?"), studentToken()).getResponse().getStatus())
                .isEqualTo(200);
    }

    // ---------- the index follows the content ----------

    @Test
    void publishingAPostAddsItToTheIndexAutomatically() throws Exception {
        String admin = adminToken();
        JsonNode session = registerAndLogin("PARENT", null);
        data(post("/api/v1/mentor/apply", Map.of("jobTitle", "Chuyên gia thử nghiệm", "yearsExperience", 9),
                session.get("accessToken").asText()), 201);
        String mentorToken = data(post("/api/v1/auth/refresh", Map.of("refreshToken", session.get("refreshToken").asText()), null), 200)
                .get("accessToken").asText();
        data(send(HttpMethod.PATCH, "/api/v1/admin/mentors/" + session.get("user").get("id").asText() + "/verify", Map.of(), admin), 200);
        String marker = "khinhkhicau" + Math.abs(System.nanoTime() % 100000);
        String postId = data(post("/api/v1/povs", Map.of("majorId", majorId("LAW"), "title", "Bài " + marker, "sections", Map.of(
                "a_day_at_work", "Một ngày " + marker, "wish_i_knew", "Điều ước " + marker, "dark_side", "Mặt trái " + marker,
                "who_fits", "Ai hợp " + marker, "school_vs_work", "Trường " + marker)), mentorToken), 201).get("id").asText();
        data(post("/api/v1/povs/" + postId + "/submit", null, mentorToken), 200);
        assertThat(store.countBySource("POV", UUID.fromString(postId))).as("a draft is not indexed").isZero();

        data(send(HttpMethod.PATCH, "/api/v1/admin/povs/" + postId + "/status", Map.of("status", "PUBLISHED"), admin), 200);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(store.countBySource("POV", UUID.fromString(postId))).isGreaterThanOrEqualTo(1));
        JsonNode answer = chat(studentToken(), "Nghề luật " + marker + " có gì đặc biệt?", majorId("LAW"), null);
        assertThat(answer.get("answer").asText()).contains("Theo chia sẻ của một Chuyên gia thử nghiệm có 9 năm kinh nghiệm");
        assertThat(StreamSupport.stream(answer.get("sources").spliterator(), false).map(s -> s.get("id").asText()).toList())
                .contains(postId);
    }

    @Test
    void changingOrDeactivatingAMajorUpdatesTheIndexAutomatically() throws Exception {
        String admin = adminToken();
        String code = "RAG_" + Math.abs(System.nanoTime() % 1_000_000);
        JsonNode created = data(post("/api/v1/admin/majors", Map.of("code", code, "name", "Ngành kiểm tra chỉ mục",
                "groupName", "Nhóm thử", "description", "Mô tả ban đầu", "combos", List.of("A00")), admin), 201);
        UUID id = UUID.fromString(created.get("id").asText());
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(store.countBySource("MAJOR", id)).isGreaterThanOrEqualTo(1));

        data(put("/api/v1/admin/majors/" + id, Map.of("name", "Ngành kiểm tra chỉ mục", "groupName", "Nhóm thử",
                "description", "Mô tả mới có từ khóa xyzduynhat", "combos", List.of("A00")), admin), 200);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "select count(*) from content_chunks where source_id = ?::uuid and text like '%xyzduynhat%'", Integer.class, id.toString()))
                .isEqualTo(1));

        assertThat(delete("/api/v1/admin/majors/" + id, admin).getResponse().getStatus()).isEqualTo(200);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(store.countBySource("MAJOR", id)).isZero());
    }
}

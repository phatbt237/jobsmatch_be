package vn.career.survey;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import vn.career.common.audit.AuditLogRepository;
import vn.career.support.AbstractIntegrationTest;

/**
 * Publishing changes which survey is active for everybody, so this class runs in its own Spring context
 * (the extra property makes Spring create a separate context, and with it separate database containers).
 */
@TestPropertySource(properties = "test.isolated-context=admin-survey")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminSurveyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AuditLogRepository auditLogs;

    // ---------- helpers ----------

    private void assertError(MvcResult result, int status, String code) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        assertThat(json(result).get("error").get("code").asText()).isEqualTo(code);
    }

    private JsonNode data(MvcResult result, int expectedStatus) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(expectedStatus);
        return json(result).get("data");
    }

    private String versionOneId() {
        return jdbc.queryForObject("select id::text from surveys where version = 1", String.class);
    }

    private JsonNode newDraft(String admin) throws Exception {
        return data(post("/api/v1/admin/surveys", Map.of("title", "Draft " + UUID.randomUUID()), admin), 201);
    }

    private JsonNode addSection(String admin, String surveyId, String code) throws Exception {
        return data(post("/api/v1/admin/surveys/" + surveyId + "/sections",
                Map.of("code", code, "title", "Section " + code), admin), 201);
    }

    private Map<String, Object> likert(String sectionId, String dimension) {
        return Map.of("sectionId", sectionId, "type", "LIKERT", "content", "Một câu hỏi mới", "dimensionCode", dimension);
    }

    // ---------- access control ----------

    @Test
    void onlyAdminsCanUseTheAdminEndpoints() throws Exception {
        String student = studentToken();
        String parent = registerAndLogin("PARENT", null).get("accessToken").asText();

        for (String path : List.of("/api/v1/admin/surveys", "/api/v1/admin/dimensions")) {
            assertError(get(path, student), 403, "ACCESS_DENIED");
            assertError(get(path, parent), 403, "ACCESS_DENIED");
            assertError(get(path, null), 401, "UNAUTHORIZED");
        }
        assertError(post("/api/v1/admin/surveys", Map.of("title", "x"), student), 403, "ACCESS_DENIED");
        assertError(post("/api/v1/admin/questions", Map.of("type", "LIKERT", "content", "x"), student),
                403, "ACCESS_DENIED");
        assertError(post("/api/v1/admin/surveys/" + versionOneId() + "/publish", null, student), 403, "ACCESS_DENIED");
    }

    // ---------- reading ----------

    @Test
    void adminsSeeDimensionsAndTheSurveyList() throws Exception {
        String admin = adminToken();

        JsonNode dimensions = data(get("/api/v1/admin/dimensions", admin), 200);
        assertThat(dimensions).hasSize(19);
        assertThat(dimensions.get(0).fieldNames()).toIterable().containsExactlyInAnyOrder("code", "name", "group");

        JsonNode page = data(get("/api/v1/admin/surveys", admin), 200);
        JsonNode v1 = null;
        for (JsonNode item : page.get("items")) {
            if (item.get("version").asInt() == 1) {
                v1 = item;
            }
        }
        assertThat(v1).isNotNull();
        assertThat(v1.get("sectionCount").asInt()).isEqualTo(4);
        assertThat(v1.get("questionCount").asInt()).isEqualTo(70);
    }

    @Test
    void adminViewIncludesTheAnswerKeyAndScoringMetadata() throws Exception {
        JsonNode survey = data(get("/api/v1/admin/surveys/" + versionOneId(), adminToken()), 200);

        boolean foundCorrectOption = false;
        boolean foundExpectedValue = false;
        boolean foundReversed = false;
        for (JsonNode section : survey.get("sections")) {
            for (JsonNode question : section.get("questions")) {
                foundExpectedValue |= !question.get("expectedValue").isNull();
                foundReversed |= question.get("reverseScored").asBoolean();
                for (JsonNode option : question.get("options")) {
                    foundCorrectOption |= option.get("correct").asBoolean();
                }
            }
        }
        assertThat(foundCorrectOption && foundExpectedValue && foundReversed).isTrue();
    }

    @Test
    void unknownIdsReturn404() throws Exception {
        String admin = adminToken();

        assertError(get("/api/v1/admin/surveys/" + UUID.randomUUID(), admin), 404, "SURVEY_NOT_FOUND");
        assertError(get("/api/v1/admin/questions/" + UUID.randomUUID(), admin), 404, "QUESTION_NOT_FOUND");
        assertError(delete("/api/v1/admin/sections/" + UUID.randomUUID(), admin), 404, "SECTION_NOT_FOUND");
    }

    // ---------- published surveys are immutable ----------

    @Test
    void aPublishedSurveyCannotBeChangedInAnyWay() throws Exception {
        String admin = adminToken();
        JsonNode v1 = data(get("/api/v1/admin/surveys/" + versionOneId(), admin), 200);
        String sectionId = v1.get("sections").get(0).get("id").asText();
        String questionId = v1.get("sections").get(0).get("questions").get(0).get("id").asText();
        Map<String, Object> newQuestion = likert(sectionId, "R");

        assertError(put("/api/v1/admin/surveys/" + v1.get("id").asText(), Map.of("title", "hacked"), admin),
                422, "SURVEY_NOT_EDITABLE");
        assertError(post("/api/v1/admin/surveys/" + v1.get("id").asText() + "/sections",
                Map.of("code", "Z", "title", "Z"), admin), 422, "SURVEY_NOT_EDITABLE");
        assertError(put("/api/v1/admin/sections/" + sectionId, Map.of("code", "A", "title", "hacked"), admin),
                422, "SURVEY_NOT_EDITABLE");
        assertError(delete("/api/v1/admin/sections/" + sectionId, admin), 422, "SURVEY_NOT_EDITABLE");
        assertError(post("/api/v1/admin/questions", newQuestion, admin), 422, "SURVEY_NOT_EDITABLE");
        assertError(put("/api/v1/admin/questions/" + questionId, newQuestion, admin), 422, "SURVEY_NOT_EDITABLE");
        assertError(delete("/api/v1/admin/questions/" + questionId, admin), 422, "SURVEY_NOT_EDITABLE");
        assertError(delete("/api/v1/admin/surveys/" + v1.get("id").asText(), admin), 422, "SURVEY_NOT_EDITABLE");

        // nothing changed
        assertThat(jdbc.queryForObject("select count(*) from questions q join survey_sections s on s.id = q.section_id "
                + "where s.survey_id = ?::uuid", Integer.class, v1.get("id").asText())).isEqualTo(70);
    }

    // ---------- new version + editing a draft ----------

    @Test
    void newVersionIsADeepCopyAsDraft() throws Exception {
        String admin = adminToken();
        JsonNode original = data(get("/api/v1/admin/surveys/" + versionOneId(), admin), 200);

        JsonNode copy = data(post("/api/v1/admin/surveys/" + original.get("id").asText() + "/new-version", null, admin), 201);

        assertThat(copy.get("status").asText()).isEqualTo("DRAFT");
        assertThat(copy.get("version").asInt()).isGreaterThan(original.get("version").asInt());
        assertThat(copy.get("id").asText()).isNotEqualTo(original.get("id").asText());
        assertThat(copy.get("sections")).hasSize(4);
        int questions = 0;
        int miniTestOptions = 0;
        for (JsonNode section : copy.get("sections")) {
            questions += section.get("questions").size();
            for (JsonNode question : section.get("questions")) {
                if ("MINI_TEST".equals(question.get("type").asText())) {
                    miniTestOptions += question.get("options").size();
                    assertThat(question.get("options").findValues("correct").stream().filter(JsonNode::asBoolean)).hasSize(1);
                }
            }
        }
        assertThat(questions).isEqualTo(70);
        assertThat(miniTestOptions).isEqualTo(24);
        // the original is untouched
        assertThat(data(get("/api/v1/admin/surveys/" + original.get("id").asText(), admin), 200).get("status").asText())
                .isIn("PUBLISHED", "ARCHIVED");
    }

    @Test
    void draftCanBeBuiltEditedAndDeleted() throws Exception {
        String admin = adminToken();
        JsonNode draft = newDraft(admin);
        String surveyId = draft.get("id").asText();
        assertThat(draft.get("status").asText()).isEqualTo("DRAFT");

        JsonNode section = addSection(admin, surveyId, "A");
        assertThat(section.get("id").isNull()).as("new section must come back with its generated id").isFalse();
        String sectionId = section.get("id").asText();
        assertThat(section.get("orderIndex").asInt()).isEqualTo(1);
        assertThat(addSection(admin, surveyId, "B").get("orderIndex").asInt()).isEqualTo(2);

        JsonNode question = data(post("/api/v1/admin/questions", likert(sectionId, "R"), admin), 201);
        assertThat(question.get("id").isNull()).as("new question must come back with its generated id").isFalse();
        assertThat(question.get("sectionId").asText()).isEqualTo(sectionId);
        assertThat(question.get("orderIndex").asInt()).isEqualTo(1);
        assertThat(question.get("weight").decimalValue()).isEqualByComparingTo("1");
        assertThat(question.get("required").asBoolean()).isTrue();
        String questionId = question.get("id").asText();

        // update: turn it into a mini test with options (options are replaced as a whole)
        Map<String, Object> asMiniTest = Map.of("type", "MINI_TEST", "content", "2 + 2 = ?", "dimensionCode", "NUMERIC",
                "weight", 4, "options", List.of(
                        Map.of("label", "3"), Map.of("label", "4", "correct", true), Map.of("label", "5")));
        JsonNode updated = data(put("/api/v1/admin/questions/" + questionId, asMiniTest, admin), 200);
        assertThat(updated.get("type").asText()).isEqualTo("MINI_TEST");
        assertThat(updated.get("options")).hasSize(3);
        assertThat(updated.get("options").get(1).get("correct").asBoolean()).isTrue();

        Map<String, Object> fewerOptions = Map.of("type", "MINI_TEST", "content", "2 + 2 = ?", "dimensionCode", "NUMERIC",
                "options", List.of(Map.of("label", "4", "correct", true), Map.of("label", "5")));
        assertThat(data(put("/api/v1/admin/questions/" + questionId, fewerOptions, admin), 200).get("options")).hasSize(2);
        assertThat(data(get("/api/v1/admin/questions/" + questionId, admin), 200).get("options")).hasSize(2);
        assertThat(jdbc.queryForObject("select count(*) from question_options where question_id = ?::uuid", Integer.class,
                questionId)).isEqualTo(2);

        // rename, rename section, delete question and section, delete survey
        assertThat(data(put("/api/v1/admin/surveys/" + surveyId, Map.of("title", "Renamed"), admin), 200).get("title").asText())
                .isEqualTo("Renamed");
        assertThat(data(put("/api/v1/admin/sections/" + sectionId,
                Map.of("code", "A", "title", "New title"), admin), 200).get("title").asText()).isEqualTo("New title");
        assertThat(delete("/api/v1/admin/questions/" + questionId, admin).getResponse().getStatus()).isEqualTo(200);
        assertThat(delete("/api/v1/admin/sections/" + sectionId, admin).getResponse().getStatus()).isEqualTo(200);
        assertThat(data(get("/api/v1/admin/surveys/" + surveyId, admin), 200).get("sections")).hasSize(1);
        assertThat(delete("/api/v1/admin/surveys/" + surveyId, admin).getResponse().getStatus()).isEqualTo(200);
        assertError(get("/api/v1/admin/surveys/" + surveyId, admin), 404, "SURVEY_NOT_FOUND");
    }

    @Test
    void duplicateSectionCodesAndOrderIndexesAreRejected() throws Exception {
        String admin = adminToken();
        String surveyId = newDraft(admin).get("id").asText();
        String sectionId = addSection(admin, surveyId, "A").get("id").asText();

        assertError(post("/api/v1/admin/surveys/" + surveyId + "/sections",
                Map.of("code", "A", "title", "again"), admin), 422, "DUPLICATE_SECTION_CODE");
        assertError(post("/api/v1/admin/surveys/" + surveyId + "/sections",
                Map.of("code", "B", "title", "same order", "orderIndex", 1), admin), 422, "DUPLICATE_ORDER_INDEX");

        data(post("/api/v1/admin/questions", likert(sectionId, "R"), admin), 201);
        Map<String, Object> sameOrder = new java.util.HashMap<>(likert(sectionId, "I"));
        sameOrder.put("orderIndex", 1);
        assertError(post("/api/v1/admin/questions", sameOrder, admin), 422, "DUPLICATE_ORDER_INDEX");
    }

    @Test
    void invalidQuestionDefinitionsAreRejectedWithDetails() throws Exception {
        String admin = adminToken();
        String surveyId = newDraft(admin).get("id").asText();
        String sectionId = addSection(admin, surveyId, "A").get("id").asText();

        // no section
        assertError(post("/api/v1/admin/questions", Map.of("type", "LIKERT", "content", "x", "dimensionCode", "R"), admin),
                400, "VALIDATION_ERROR");
        // unknown dimension
        MvcResult unknown = post("/api/v1/admin/questions", likert(sectionId, "NOPE"), admin);
        assertError(unknown, 400, "VALIDATION_ERROR");
        assertThat(json(unknown).get("error").get("details").get(0).get("field").asText()).isEqualTo("dimensionCode");
        // mini test without a correct option
        assertError(post("/api/v1/admin/questions", Map.of("sectionId", sectionId, "type", "MINI_TEST", "content", "x",
                "dimensionCode", "LOGIC", "options", List.of(Map.of("label", "a"), Map.of("label", "b"))), admin),
                400, "VALIDATION_ERROR");
        // attention check that also measures a dimension
        assertError(post("/api/v1/admin/questions", Map.of("sectionId", sectionId, "type", "LIKERT", "content", "x",
                "dimensionCode", "R", "attentionCheck", true, "expectedValue", 4), admin), 400, "VALIDATION_ERROR");
        // blank content, missing type, bad weight
        assertError(post("/api/v1/admin/questions", Map.of("sectionId", sectionId, "content", " "), admin),
                400, "VALIDATION_ERROR");
        Map<String, Object> badWeight = new java.util.HashMap<>(likert(sectionId, "R"));
        badWeight.put("weight", 0);
        assertError(post("/api/v1/admin/questions", badWeight, admin), 400, "VALIDATION_ERROR");

        // a valid attention check is accepted and the student view still hides it
        Map<String, Object> attention = Map.of("sectionId", sectionId, "type", "LIKERT",
                "content", "Chọn Đồng ý", "attentionCheck", true, "expectedValue", 4);
        JsonNode created = data(post("/api/v1/admin/questions", attention, admin), 201);
        assertThat(created.get("attentionCheck").asBoolean()).isTrue();
        assertThat(created.get("expectedValue").asInt()).isEqualTo(4);
    }

    @Test
    void publishingAnEmptyDraftIsRejected() throws Exception {
        String admin = adminToken();
        String surveyId = newDraft(admin).get("id").asText();
        addSection(admin, surveyId, "A");

        assertError(post("/api/v1/admin/surveys/" + surveyId + "/publish", null, admin), 422, "SURVEY_NOT_PUBLISHABLE");
    }

    // ---------- publishing (runs last: it changes the active survey) ----------

    @Test
    @Order(100)
    void publishingArchivesTheOldVersionButRunningAttemptsKeepTheirVersion() throws Exception {
        String admin = adminToken();

        // A student starts on the currently active survey (v1)
        String oldStudent = studentToken();
        JsonNode oldAttempt = data(post("/api/v1/attempts", null, oldStudent), 201);
        assertThat(oldAttempt.get("surveyVersion").asInt()).isEqualTo(1);
        String oldQuestionId = data(get("/api/v1/surveys/active", oldStudent), 200)
                .get("sections").get(0).get("questions").get(0).get("id").asText();

        // Admin prepares version 2 and publishes it
        JsonNode v2 = data(post("/api/v1/admin/surveys/" + versionOneId() + "/new-version", null, admin), 201);
        String v2Id = v2.get("id").asText();
        JsonNode published = data(post("/api/v1/admin/surveys/" + v2Id + "/publish", null, admin), 200);

        assertThat(published.get("status").asText()).isEqualTo("PUBLISHED");
        assertThat(published.get("publishedAt").asText()).isNotBlank();
        assertThat(jdbc.queryForObject("select status from surveys where version = 1", String.class)).isEqualTo("ARCHIVED");
        assertThat(jdbc.queryForObject("select count(*) from surveys where status = 'PUBLISHED'", Integer.class)).isEqualTo(1);
        assertThat(auditLogs.findByEntityTypeAndEntityId("Survey", UUID.fromString(v2Id))).hasSize(1);
        assertThat(auditLogs.findByEntityTypeAndEntityId("Survey", UUID.fromString(v2Id)).get(0).getAction())
                .isEqualTo("SURVEY_PUBLISHED");

        // New students get v2, the running attempt is still on v1 and still editable
        assertThat(data(get("/api/v1/surveys/active", studentToken()), 200).get("version").asInt()).isEqualTo(2);
        assertThat(data(post("/api/v1/attempts", null, studentToken()), 201).get("surveyVersion").asInt()).isEqualTo(2);
        assertThat(data(post("/api/v1/attempts", null, oldStudent), 200).get("id").asText())
                .isEqualTo(oldAttempt.get("id").asText());
        // the running attempt is still rendered from ITS survey version, not from the newly active one
        JsonNode attemptSurvey = data(get("/api/v1/attempts/" + oldAttempt.get("id").asText() + "/survey", oldStudent), 200);
        assertThat(attemptSurvey.get("version").asInt()).isEqualTo(1);
        assertThat(attemptSurvey.get("id").asText()).isNotEqualTo(v2Id);
        MvcResult save = put("/api/v1/attempts/" + oldAttempt.get("id").asText() + "/answers",
                Map.of("answers", List.of(Map.of("questionId", oldQuestionId, "value", 4))), oldStudent);
        assertThat(save.getResponse().getStatus()).isEqualTo(200);

        // The published version is now frozen, an archived one cannot be published again
        assertError(post("/api/v1/admin/surveys/" + v2Id + "/publish", null, admin), 422, "SURVEY_NOT_PUBLISHABLE");
        assertError(post("/api/v1/admin/surveys/" + versionOneId() + "/publish", null, admin), 422, "SURVEY_NOT_PUBLISHABLE");
        assertError(put("/api/v1/admin/surveys/" + v2Id, Map.of("title", "late edit"), admin), 422, "SURVEY_NOT_EDITABLE");
    }
}

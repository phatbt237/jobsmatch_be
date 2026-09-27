package vn.career.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import vn.career.common.audit.AuditLog;
import vn.career.common.audit.AuditLogRepository;
import vn.career.support.AbstractIntegrationTest;
import vn.career.support.SurveyTestSupport;
import vn.career.support.SurveyTestSupport.Persona;

/** Submit, scoring, matching and who may read the result. */
class SubmitIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AuditLogRepository auditLogs;

    private SurveyTestSupport survey;

    @BeforeEach
    void setUp() {
        survey = new SurveyTestSupport(jdbc, objectMapper);
    }

    // ---------- helpers ----------

    private record Student(String userId, String email, String token, String attemptId) {
    }

    private JsonNode data(MvcResult result, int status) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        return json(result).get("data");
    }

    private void assertError(MvcResult result, int status, String code) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        assertThat(json(result).get("error").get("code").asText()).isEqualTo(code);
    }

    private static Map<String, Object> constraints() {
        return Map.of("gpa", Map.of("toan", 8.5, "van", 7.5, "anh", 8.0, "ly", 8.0, "hoa", 7.0, "sinh", 7.0,
                "su", 7.0, "dia", 7.0), "combos", List.of("A00", "A01", "D01"), "preferredRegions", List.of("NORTH", "SOUTH"),
                "budgetPerYear", 60_000_000, "familyPressure", 2);
    }

    /** Registers a student, starts an attempt and fills in every answer (but does not submit). */
    private Student startedStudent(Persona persona, Map<String, Object> constraints) throws Exception {
        JsonNode session = registerAndLogin("STUDENT", yearsAgo(17));
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

    private JsonNode submit(Student student) throws Exception {
        return data(post("/api/v1/attempts/" + student.attemptId() + "/submit", null, student.token()), 200);
    }

    private static List<String> majorCodes(JsonNode result) {
        return StreamSupport.stream(result.get("recommendations").spliterator(), false)
                .map(r -> r.get("majorCode").asText()).toList();
    }

    // ---------- the result ----------

    @Test
    void analyticalStudentGetsAnalyticalMajorsAndNoDesign() throws Exception {
        Persona analytical = Persona.of(2, "I", 5, "C", 5, "R", 3, "LOGIC", 5, "NUMERIC", 5, "VAL_STABILITY", 5, "VAL_INCOME", 4);
        Student student = startedStudent(analytical, constraints());

        JsonNode result = submit(student);

        assertThat(result.get("attemptStatus").asText()).isEqualTo("SCORED");
        assertThat(result.get("recommendations")).hasSize(5);
        assertThat(majorCodes(result).subList(0, 3)).containsAnyOf("ACCOUNTING", "DS", "IT", "CYBERSEC", "FINANCE");
        assertThat(majorCodes(result)).doesNotContain("GRAPHICDESIGN", "MULTIMEDIA", "TOURISM", "SOCIALWORK");
        assertThat(result.get("algoVersion").asText()).isEqualTo("v1");
        assertThat(result.get("disclaimer").asText()).isNotBlank();
        assertThat(result.get("explanationStatus").asText()).isIn("PENDING", "DONE");
    }

    @Test
    void creativeStudentGetsCreativeMajors() throws Exception {
        Persona creative = Persona.of(2, "A", 5, "CREATIVE", 5, "SPATIAL", 5, "VAL_CREATIVITY", 5, "VAL_AUTONOMY", 5);
        Student student = startedStudent(creative, Map.of("combos", List.of("A00", "A01", "D01")));

        List<String> codes = majorCodes(submit(student));

        assertThat(codes.subList(0, 3)).containsAnyOf("GRAPHICDESIGN", "MULTIMEDIA", "ARCHITECTURE");
        assertThat(codes).doesNotContain("ACCOUNTING", "CYBERSEC");
    }

    @Test
    void helpingStudentGetsPeopleMajors() throws Exception {
        Persona helper = Persona.of(2, "S", 5, "SOCIAL_SKILL", 5, "VAL_HELPING", 5, "VAL_TEAMWORK", 5, "I", 3);
        Student student = startedStudent(helper, Map.of("combos", List.of("B00", "C00", "D01")));

        List<String> codes = majorCodes(submit(student));

        assertThat(codes.subList(0, 3)).containsAnyOf("NURSING", "PSYCHOLOGY", "SOCIALWORK", "MEDICINE", "EDUCATION");
    }

    @Test
    void everyRecommendationCarriesARankAScoreAndAnExplainableBreakdown() throws Exception {
        JsonNode result = submit(startedStudent(Persona.of(3, "I", 5), constraints()));

        double previous = 1.0;
        int expectedRank = 1;
        for (JsonNode rec : result.get("recommendations")) {
            assertThat(rec.get("rank").asInt()).isEqualTo(expectedRank++);
            assertThat(rec.get("score").asDouble()).isBetween(0.0, 1.0).isLessThanOrEqualTo(previous);
            previous = rec.get("score").asDouble();
            assertThat(rec.get("majorName").asText()).isNotBlank();
            assertThat(rec.get("groupName").asText()).isNotBlank();
            JsonNode breakdown = rec.get("breakdown");
            assertThat(breakdown.fieldNames()).toIterable()
                    .contains("interest", "aptitude", "values", "base", "penalties", "final");
            assertThat(breakdown.get("interest").asDouble()).isBetween(0.0, 1.0);
            double base = 0.5 * breakdown.get("interest").asDouble() + 0.25 * breakdown.get("aptitude").asDouble()
                    + 0.25 * breakdown.get("values").asDouble();
            assertThat(breakdown.get("base").asDouble()).isEqualTo(base, org.assertj.core.data.Offset.offset(0.001));
            assertThat(rec.get("explanationStatus").asText()).isIn("PENDING", "DONE");
        }
    }

    @Test
    void resultListsAllNineteenDimensionsAsPercentages() throws Exception {
        JsonNode result = submit(startedStudent(Persona.of(1, "I", 5, "C", 5), constraints()));

        JsonNode dimensions = result.get("dimensions");
        assertThat(dimensions).hasSize(19);
        Map<String, Double> percent = StreamSupport.stream(dimensions.spliterator(), false)
                .collect(Collectors.toMap(d -> d.get("code").asText(), d -> d.get("percent").asDouble()));
        assertThat(percent.get("I")).isEqualTo(100.0);
        assertThat(percent.get("C")).isEqualTo(100.0);
        assertThat(percent.get("A")).isEqualTo(0.0);
        assertThat(percent.values()).allSatisfy(p -> assertThat(p).isBetween(0.0, 100.0));
        assertThat(dimensions.get(0).get("name").asText()).isNotBlank();
        assertThat(dimensions.get(0).get("group").asText()).isIn("INTEREST", "APTITUDE", "VALUE");
        // the raw scores were persisted
        assertThat(jdbc.queryForObject("select count(*) from attempt_scores where attempt_id = ?::uuid", Integer.class,
                result.get("attemptId").asText())).isEqualTo(19);
    }

    @Test
    void reverseScoredStatementsAreScoredMirrored() throws Exception {
        // Persona answers reversed statements mirrored, so a target of 5 must still end up as exactly 100%
        JsonNode result = submit(startedStudent(Persona.of(3, "E", 5), constraints()));

        double e = StreamSupport.stream(result.get("dimensions").spliterator(), false)
                .filter(d -> d.get("code").asText().equals("E")).findFirst().orElseThrow().get("percent").asDouble();
        assertThat(e).isEqualTo(100.0);
    }

    @Test
    void miniTestsContributeToTheAptitudeScore() throws Exception {
        JsonNode right = submit(startedStudent(Persona.of(3), constraints()));
        JsonNode wrong = submit(startedStudent(Persona.of(3).withWrongMiniTests(), constraints()));

        assertThat(percentOf(right, "LOGIC")).isGreaterThan(percentOf(wrong, "LOGIC"));
        assertThat(percentOf(right, "NUMERIC")).isGreaterThan(percentOf(wrong, "NUMERIC"));
        assertThat(percentOf(right, "VERBAL")).isGreaterThan(percentOf(wrong, "VERBAL"));
    }

    private static double percentOf(JsonNode result, String code) {
        return StreamSupport.stream(result.get("dimensions").spliterator(), false)
                .filter(d -> d.get("code").asText().equals(code)).findFirst().orElseThrow().get("percent").asDouble();
    }

    // ---------- constraints influence ----------

    @Test
    void examCombinationsActAsAHardFilter() throws Exception {
        Student student = startedStudent(Persona.of(3, "S", 5, "E", 4), Map.of("combos", List.of("C00")));

        JsonNode result = submit(student);

        for (String code : majorCodes(result)) {
            String combos = jdbc.queryForObject("select array_to_string(combos, ',') from majors where code = ?", String.class, code);
            assertThat(combos).as(code).contains("C00");
        }
    }

    @Test
    void aBudgetFarBelowEveryTuitionAddsAPenaltyToEachRecommendation() throws Exception {
        Persona persona = Persona.of(3, "I", 5, "C", 5);
        JsonNode without = submit(startedStudent(persona, Map.of("combos", List.of("A00", "A01", "D01"))));
        JsonNode broke = submit(startedStudent(persona, Map.of("combos", List.of("A00", "A01", "D01"),
                "budgetPerYear", 1_000_000, "preferredRegions", List.of("NORTH"))));

        for (JsonNode rec : broke.get("recommendations")) {
            JsonNode penalties = rec.get("breakdown").get("penalties");
            assertThat(penalties).extracting(p -> p.get("code").asText()).contains("BUDGET_EXCEEDED");
            assertThat(penalties.get(0).get("amount").asDouble()).isEqualTo(0.1);
            assertThat(rec.get("score").asDouble()).isLessThan(rec.get("breakdown").get("base").asDouble());
        }
        for (JsonNode rec : without.get("recommendations")) {
            assertThat(rec.get("breakdown").get("penalties")).isEmpty();
        }
    }

    @Test
    void lowGradesAddAScorePenaltyForCompetitiveMajors() throws Exception {
        Map<String, Object> weak = Map.of("gpa", Map.of("toan", 5.0, "van", 5.0, "anh", 5.0, "ly", 5.0, "hoa", 5.0),
                "combos", List.of("A00", "D01"));

        JsonNode result = submit(startedStudent(Persona.of(2, "I", 5, "C", 5, "LOGIC", 5), weak));

        boolean anyPenalised = StreamSupport.stream(result.get("recommendations").spliterator(), false)
                .anyMatch(r -> StreamSupport.stream(r.get("breakdown").get("penalties").spliterator(), false)
                        .anyMatch(p -> p.get("code").asText().equals("SCORE_BELOW_CUTOFF")));
        assertThat(anyPenalised).isTrue();
    }

    // ---------- data quality ----------

    @Test
    void carefulAnswersAreReliable() throws Exception {
        JsonNode result = submit(startedStudent(Persona.of(2, "I", 5, "C", 4, "A", 1), constraints()));

        assertThat(result.get("lowReliability").asBoolean()).isFalse();
        assertThat(result.get("qualityFlags").get("ATTENTION_FAILED").asBoolean()).isFalse();
        assertThat(result.get("qualityFlags").get("STRAIGHT_LINING").asBoolean()).isFalse();
    }

    @Test
    void straightLiningMakesTheResultLowReliabilityButStillReturnsRecommendations() throws Exception {
        JsonNode session = registerAndLogin("STUDENT", yearsAgo(17));
        String token = session.get("accessToken").asText();
        String attemptId = data(post("/api/v1/attempts", null, token), 201).get("id").asText();
        data(put("/api/v1/attempts/" + attemptId + "/answers", Map.of("answers", survey.straightLined(1, 4)), token), 200);
        data(put("/api/v1/attempts/" + attemptId + "/constraints", constraints(), token), 200);

        JsonNode result = data(post("/api/v1/attempts/" + attemptId + "/submit", null, token), 200);

        assertThat(result.get("lowReliability").asBoolean()).isTrue();
        assertThat(result.get("qualityFlags").get("STRAIGHT_LINING").asBoolean()).isTrue();
        assertThat(result.get("recommendations")).isNotEmpty();
    }

    @Test
    void failingBothAttentionChecksMakesTheResultLowReliability() throws Exception {
        JsonNode result = submit(startedStudent(Persona.of(2, "I", 5, "C", 4, "A", 1).withAttentionFailing(), constraints()));

        assertThat(result.get("qualityFlags").get("ATTENTION_FAILED").asBoolean()).isTrue();
        assertThat(result.get("lowReliability").asBoolean()).isTrue();
    }

    @Test
    void finishingInSecondsIsFlaggedAsTooFast() throws Exception {
        JsonNode result = submit(startedStudent(Persona.of(2, "I", 5), constraints()));

        assertThat(result.get("qualityFlags").get("TOO_FAST").asBoolean()).isTrue();
        // TOO_FAST alone is only a warning
        assertThat(result.get("lowReliability").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("select quality_flags::text from survey_attempts where id = ?::uuid", String.class,
                result.get("attemptId").asText())).contains("TOO_FAST");
    }

    // ---------- submit rules ----------

    @Test
    void submittingAnIncompleteAttemptListsWhatIsMissing() throws Exception {
        Student student = startedStudent(null, null);

        MvcResult result = post("/api/v1/attempts/" + student.attemptId() + "/submit", null, student.token());

        assertError(result, 422, "SURVEY_INCOMPLETE");
        assertThat(json(result).get("error").get("details").size()).isGreaterThanOrEqualTo(3);
        // nothing was scored and the attempt is still editable
        assertThat(jdbc.queryForObject("select status from survey_attempts where id = ?::uuid", String.class, student.attemptId()))
                .isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForObject("select count(*) from recommendations where attempt_id = ?::uuid", Integer.class,
                student.attemptId())).isZero();
    }

    @Test
    void allAnswersButNoSectionDConstraintsIsStillIncomplete() throws Exception {
        Student student = startedStudent(Persona.of(3), null);

        assertError(post("/api/v1/attempts/" + student.attemptId() + "/submit", null, student.token()), 422, "SURVEY_INCOMPLETE");

        data(put("/api/v1/attempts/" + student.attemptId() + "/constraints", Map.of("familyPressure", 2), student.token()), 200);
        assertThat(submit(student).get("attemptStatus").asText()).isEqualTo("SCORED");
    }

    @Test
    void anAttemptCanOnlyBeSubmittedOnce() throws Exception {
        Student student = startedStudent(Persona.of(3, "I", 5), constraints());
        submit(student);

        assertError(post("/api/v1/attempts/" + student.attemptId() + "/submit", null, student.token()), 422, "ATTEMPT_NOT_EDITABLE");
        assertThat(jdbc.queryForObject("select count(*) from recommendations where attempt_id = ?::uuid", Integer.class,
                student.attemptId())).isEqualTo(5);
        // answers are frozen too
        String questionId = jdbc.queryForObject("select id::text from questions limit 1", String.class);
        assertError(put("/api/v1/attempts/" + student.attemptId() + "/answers",
                Map.of("answers", List.of(Map.of("questionId", questionId, "value", 3))), student.token()), 422, "ATTEMPT_NOT_EDITABLE");
    }

    @Test
    void afterSubmittingAStudentCanStartAFreshAttempt() throws Exception {
        Student student = startedStudent(Persona.of(3, "I", 5), constraints());
        submit(student);

        JsonNode next = data(post("/api/v1/attempts", null, student.token()), 201);

        assertThat(next.get("id").asText()).isNotEqualTo(student.attemptId());
        assertThat(next.get("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(data(get("/api/v1/me/attempts", student.token()), 200).get("totalItems").asInt()).isEqualTo(2);
    }

    @Test
    void onlyTheOwningStudentCanSubmit() throws Exception {
        Student owner = startedStudent(Persona.of(3), constraints());
        Student other = startedStudent(null, null);
        String parent = registerAndLogin("PARENT", null).get("accessToken").asText();
        String path = "/api/v1/attempts/" + owner.attemptId() + "/submit";

        assertError(post(path, null, other.token()), 403, "ACCESS_DENIED");
        assertError(post(path, null, parent), 403, "ACCESS_DENIED");
        assertError(post(path, null, adminToken()), 403, "ACCESS_DENIED");
        assertError(post(path, null, null), 401, "UNAUTHORIZED");
        assertError(post("/api/v1/attempts/" + UUID.randomUUID() + "/submit", null, owner.token()), 404, "ATTEMPT_NOT_FOUND");
        // and the owner's attempt is untouched
        assertThat(jdbc.queryForObject("select status from survey_attempts where id = ?::uuid", String.class, owner.attemptId()))
                .isEqualTo("IN_PROGRESS");
    }

    // ---------- who can read the result ----------

    @Test
    void resultIsNotAvailableBeforeSubmitting() throws Exception {
        Student student = startedStudent(Persona.of(3), constraints());

        assertError(get("/api/v1/attempts/" + student.attemptId() + "/result", student.token()), 422, "RESULT_NOT_READY");
    }

    @Test
    void theOwnerCanReadTheResultAgainWithoutAnAuditEntry() throws Exception {
        Student student = startedStudent(Persona.of(3, "I", 5), constraints());
        JsonNode submitted = submit(student);

        JsonNode again = data(get("/api/v1/attempts/" + student.attemptId() + "/result", student.token()), 200);

        assertThat(majorCodes(again)).isEqualTo(majorCodes(submitted));
        assertThat(auditActions(student.attemptId())).doesNotContain("RESULT_VIEWED_BY_OTHER");
    }

    @Test
    void anotherStudentCannotReadTheResult() throws Exception {
        Student owner = startedStudent(Persona.of(3, "I", 5), constraints());
        submit(owner);
        String other = studentToken();

        assertError(get("/api/v1/attempts/" + owner.attemptId() + "/result", other), 403, "ACCESS_DENIED");
        assertError(get("/api/v1/attempts/" + owner.attemptId() + "/result", null), 401, "UNAUTHORIZED");
        assertError(get("/api/v1/attempts/" + owner.attemptId() + "/result/stream", other), 403, "ACCESS_DENIED");
    }

    @Test
    void anApprovedParentCanReadTheResultAndTheViewIsAudited() throws Exception {
        Student student = startedStudent(Persona.of(3, "I", 5), constraints());
        submit(student);
        JsonNode parent = registerAndLogin("PARENT", null);
        String parentToken = parent.get("accessToken").asText();
        String inviteCode = data(post("/api/v1/parent/invites", null, student.token()), 200).get("inviteCode").asText();
        data(post("/api/v1/parent/links", Map.of("inviteCode", inviteCode, "consent", true), parentToken), 200);

        JsonNode result = data(get("/api/v1/attempts/" + student.attemptId() + "/result", parentToken), 200);

        assertThat(result.get("recommendations")).hasSize(5);
        List<AuditLog> audit = auditLogs.findByEntityTypeAndEntityId("SurveyAttempt", UUID.fromString(student.attemptId()));
        assertThat(audit).hasSize(1);
        assertThat(audit.get(0).getAction()).isEqualTo("RESULT_VIEWED_BY_OTHER");
        assertThat(audit.get(0).getActorId().toString()).isEqualTo(parent.get("user").get("id").asText());
        assertThat(audit.get(0).getMetadata()).containsEntry("studentId", student.userId()).containsEntry("viewerRole", "PARENT");
    }

    @Test
    void aParentWithoutAnApprovedLinkCannotReadTheResult() throws Exception {
        Student student = startedStudent(Persona.of(3, "I", 5), constraints());
        submit(student);
        String stranger = registerAndLogin("PARENT", null).get("accessToken").asText();
        // a parent who only has a pending invite is not linked either
        data(post("/api/v1/parent/invites", null, student.token()), 200);

        assertError(get("/api/v1/attempts/" + student.attemptId() + "/result", stranger), 403, "ACCESS_DENIED");
        assertThat(auditActions(student.attemptId())).isEmpty();
    }

    @Test
    void adminsCanReadAResultAndTheViewIsAudited() throws Exception {
        Student student = startedStudent(Persona.of(3, "I", 5), constraints());
        submit(student);

        JsonNode result = data(get("/api/v1/attempts/" + student.attemptId() + "/result", adminToken()), 200);

        assertThat(result.get("recommendations")).hasSize(5);
        assertThat(auditActions(student.attemptId())).containsExactly("RESULT_VIEWED_BY_OTHER");
    }

    private List<String> auditActions(String attemptId) {
        return new ArrayList<>(auditLogs.findByEntityTypeAndEntityId("SurveyAttempt", UUID.fromString(attemptId)).stream()
                .map(AuditLog::getAction).toList());
    }

    @Test
    void resultsOfDifferentStudentsAreIndependent() throws Exception {
        JsonNode analytical = submit(startedStudent(Persona.of(2, "I", 5, "C", 5, "LOGIC", 5), constraints()));
        JsonNode creative = submit(startedStudent(Persona.of(2, "A", 5, "CREATIVE", 5), constraints()));

        Set<String> a = Set.copyOf(majorCodes(analytical));
        Set<String> c = Set.copyOf(majorCodes(creative));
        assertThat(a).isNotEqualTo(c);
    }
}

package vn.career.survey;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import vn.career.support.AbstractIntegrationTest;

class SurveyAttemptIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    // ---------- helpers ----------

    private record Student(String email, String token, String attemptId) {
    }

    private JsonNode activeSurvey(String token) throws Exception {
        return json(get("/api/v1/surveys/active", token)).get("data");
    }

    /** A valid answer for every question of one section (or all sections when code is null). */
    private ArrayNode validAnswers(JsonNode survey, String sectionCode) {
        ArrayNode answers = objectMapper.createArrayNode();
        for (JsonNode section : survey.get("sections")) {
            if (sectionCode != null && !sectionCode.equals(section.get("code").asText())) {
                continue;
            }
            for (JsonNode question : section.get("questions")) {
                ObjectNode item = answers.addObject();
                item.put("questionId", question.get("id").asText());
                switch (question.get("type").asText()) {
                    case "LIKERT" -> item.put("value", 3);
                    case "NUMERIC_INPUT" -> item.put("value", 1);
                    case "SINGLE_CHOICE", "MINI_TEST" -> item.put("value", question.get("options").get(0).get("id").asText());
                    case "RANKING" -> {
                        ArrayNode ranking = item.putArray("value");
                        question.get("options").forEach(o -> ranking.add(o.get("id").asText()));
                    }
                    default -> throw new IllegalStateException("unexpected question type");
                }
            }
        }
        return answers;
    }

    private Student startStudent() throws Exception {
        String email = uniqueEmail();
        register(email, "STUDENT", yearsAgo(17));
        String token = loginOrFail(email, PASSWORD).get("accessToken").asText();
        MvcResult started = post("/api/v1/attempts", null, token);
        assertThat(started.getResponse().getStatus()).isEqualTo(201);
        return new Student(email, token, json(started).get("data").get("id").asText());
    }

    private MvcResult saveAnswers(Student student, ArrayNode answers) throws Exception {
        return put("/api/v1/attempts/" + student.attemptId() + "/answers", Map.of("answers", answers), student.token());
    }

    private JsonNode progressOf(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(200);
        return json(result).get("data");
    }

    private void assertError(MvcResult result, int status, String code) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        assertThat(json(result).get("error").get("code").asText()).isEqualTo(code);
    }

    private static Map<String, Object> validConstraints() {
        return Map.of(
                "gpa", Map.of("toan", 8.5, "van", 7.0, "anh", 9.0),
                "combos", List.of("A00", "D01"),
                "preferredRegions", List.of("NORTH", "SOUTH"),
                "budgetPerYear", 40_000_000,
                "familyPressure", 3);
    }

    // ---------- start ----------

    @Test
    void startingTwiceReturnsTheSameUnfinishedAttempt() throws Exception {
        Student student = startStudent();

        MvcResult again = post("/api/v1/attempts", null, student.token());

        assertThat(again.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(again).get("data").get("id").asText()).isEqualTo(student.attemptId());
        assertThat(json(again).get("data").get("status").asText()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void onlyStudentsCanStartAnAttempt() throws Exception {
        String parent = registerAndLogin("PARENT", null).get("accessToken").asText();

        assertError(post("/api/v1/attempts", null, parent), 403, "ACCESS_DENIED");
        assertError(post("/api/v1/attempts", null, adminToken()), 403, "ACCESS_DENIED");
        assertError(post("/api/v1/attempts", null, null), 401, "UNAUTHORIZED");
    }

    @Test
    void studentsWaitingForParentalConsentCanStillTakeTheSurvey() throws Exception {
        String token = registerAndLogin("STUDENT", yearsAgo(15)).get("accessToken").asText();

        assertThat(post("/api/v1/attempts", null, token).getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void theAttemptSurveyIsTheSameSurveyWithoutAnyScoringData() throws Exception {
        Student student = startStudent();

        MvcResult result = get("/api/v1/attempts/" + student.attemptId() + "/survey", student.token());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("expectedValue", "correct", "dimension", "weight", "reverse", "attention");
        JsonNode survey = json(result).get("data");
        assertThat(survey.get("sections")).hasSize(4);
        assertThat(survey.get("likertScale")).hasSize(5);
        assertThat(survey.get("version").asInt()).isEqualTo(1);
        assertThat(survey.get("id").asText()).isEqualTo(activeSurvey(student.token()).get("id").asText());
    }

    @Test
    void onlyTheOwnerCanReadTheSurveyOfAnAttempt() throws Exception {
        Student owner = startStudent();
        Student other = startStudent();
        String path = "/api/v1/attempts/" + owner.attemptId() + "/survey";

        assertError(get(path, other.token()), 403, "ACCESS_DENIED");
        assertError(get(path, null), 401, "UNAUTHORIZED");
        assertError(get("/api/v1/attempts/" + UUID.randomUUID() + "/survey", owner.token()), 404, "ATTEMPT_NOT_FOUND");
        assertError(get(path, registerAndLogin("PARENT", null).get("accessToken").asText()), 403, "ACCESS_DENIED");
    }

    // ---------- end-to-end ----------

    @Test
    void studentCanCompleteTheWholeSurveySectionBySection() throws Exception {
        Student student = startStudent();
        JsonNode survey = activeSurvey(student.token());

        JsonNode afterA = progressOf(saveAnswers(student, validAnswers(survey, "A")));
        assertThat(afterA.get("sections").get(0).get("complete").asBoolean()).isTrue();
        assertThat(afterA.get("currentSectionCode").asText()).isEqualTo("B");
        assertThat(afterA.get("readyToSubmit").asBoolean()).isFalse();

        progressOf(saveAnswers(student, validAnswers(survey, "B")));
        JsonNode afterC = progressOf(saveAnswers(student, validAnswers(survey, "C")));
        assertThat(afterC.get("requiredAnswered").asInt()).isEqualTo(afterC.get("requiredQuestions").asInt());
        assertThat(afterC.get("currentSectionCode").asText()).isEqualTo("D");
        assertThat(afterC.get("constraintsSaved").asBoolean()).isFalse();
        assertThat(afterC.get("readyToSubmit").asBoolean()).isFalse();

        MvcResult constraints = put("/api/v1/attempts/" + student.attemptId() + "/constraints",
                validConstraints(), student.token());
        assertThat(constraints.getResponse().getStatus()).isEqualTo(200);

        JsonNode detail = json(get("/api/v1/attempts/" + student.attemptId(), student.token())).get("data");
        assertThat(detail.get("progress").get("readyToSubmit").asBoolean()).isTrue();
        assertThat(detail.get("progress").get("constraintsSaved").asBoolean()).isTrue();
        assertThat(detail.get("answers")).hasSize(afterC.get("totalQuestions").asInt());
    }

    @Test
    void progressSurvivesLeavingAndComingBackInANewSession() throws Exception {
        Student student = startStudent();
        JsonNode survey = activeSurvey(student.token());
        ArrayNode partial = validAnswers(survey, "A");
        while (partial.size() > 10) {
            partial.remove(partial.size() - 1);
        }
        saveAnswers(student, partial);
        put("/api/v1/attempts/" + student.attemptId() + "/constraints", validConstraints(), student.token());

        // "Come back later": log in again with a brand new token
        String newToken = loginOrFail(student.email(), PASSWORD).get("accessToken").asText();
        MvcResult resumed = post("/api/v1/attempts", null, newToken);
        assertThat(resumed.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(resumed).get("data").get("id").asText()).isEqualTo(student.attemptId());

        JsonNode detail = json(get("/api/v1/attempts/" + student.attemptId(), newToken)).get("data");
        assertThat(detail.get("answers")).hasSize(10);
        assertThat(detail.get("progress").get("answeredQuestions").asInt()).isEqualTo(10);
        assertThat(detail.get("constraints").get("familyPressure").asInt()).isEqualTo(3);
        assertThat(detail.get("constraints").get("combos")).extracting(JsonNode::asText).containsExactly("A00", "D01");
        assertThat(detail.get("constraints").get("gpa").get("toan").decimalValue()).isEqualByComparingTo("8.5");
        // saved values come back exactly as sent
        JsonNode first = detail.get("answers").get(0);
        assertThat(first.get("value").asInt()).isEqualTo(3);
        assertThat(first.get("answeredAt").asText()).isNotBlank();
    }

    @Test
    void savingAnAnswerAgainOverwritesItInsteadOfDuplicating() throws Exception {
        Student student = startStudent();
        JsonNode survey = activeSurvey(student.token());
        String likertId = survey.get("sections").get(0).get("questions").get(0).get("id").asText();

        saveAnswers(student, singleAnswer(likertId, 2));
        progressOf(saveAnswers(student, singleAnswer(likertId, 5)));

        JsonNode detail = json(get("/api/v1/attempts/" + student.attemptId(), student.token())).get("data");
        assertThat(detail.get("answers")).hasSize(1);
        assertThat(detail.get("answers").get(0).get("value").asInt()).isEqualTo(5);
        assertThat(jdbc.queryForObject("select count(*) from answers where attempt_id = ?::uuid", Integer.class,
                student.attemptId())).isEqualTo(1);
    }

    private ArrayNode singleAnswer(String questionId, int value) {
        ArrayNode answers = objectMapper.createArrayNode();
        answers.addObject().put("questionId", questionId).put("value", value);
        return answers;
    }

    // ---------- validation ----------

    @Test
    void aBatchWithOneInvalidAnswerIsRejectedCompletely() throws Exception {
        Student student = startStudent();
        JsonNode questions = activeSurvey(student.token()).get("sections").get(0).get("questions");
        ArrayNode batch = objectMapper.createArrayNode();
        batch.addObject().put("questionId", questions.get(0).get("id").asText()).put("value", 4);   // valid
        batch.addObject().put("questionId", questions.get(1).get("id").asText()).put("value", 9);   // invalid

        MvcResult result = saveAnswers(student, batch);

        assertError(result, 400, "VALIDATION_ERROR");
        assertThat(json(result).get("error").get("details").get(0).get("field").asText()).isEqualTo("answers[1].value");
        JsonNode detail = json(get("/api/v1/attempts/" + student.attemptId(), student.token())).get("data");
        assertThat(detail.get("answers")).isEmpty();
    }

    @Test
    void answersToUnknownOrRepeatedQuestionsAreRejected() throws Exception {
        Student student = startStudent();
        String questionId = activeSurvey(student.token()).get("sections").get(0).get("questions").get(0).get("id").asText();

        assertError(saveAnswers(student, singleAnswer(UUID.randomUUID().toString(), 3)), 400, "VALIDATION_ERROR");

        ArrayNode repeated = singleAnswer(questionId, 3);
        repeated.addObject().put("questionId", questionId).put("value", 4);
        assertError(saveAnswers(student, repeated), 400, "VALIDATION_ERROR");
    }

    @Test
    void wrongAnswerShapeForTheQuestionTypeIsRejected() throws Exception {
        Student student = startStudent();
        JsonNode survey = activeSurvey(student.token());
        String miniTestId = null;
        for (JsonNode question : survey.get("sections").get(1).get("questions")) {
            if ("MINI_TEST".equals(question.get("type").asText())) {
                miniTestId = question.get("id").asText();
            }
        }

        // a Likert-style number where an option id is expected
        assertError(saveAnswers(student, singleAnswer(miniTestId, 3)), 400, "VALIDATION_ERROR");
    }

    @Test
    void emptyOrMalformedBatchesAreRejected() throws Exception {
        Student student = startStudent();

        assertError(saveAnswers(student, objectMapper.createArrayNode()), 400, "VALIDATION_ERROR");
        assertError(put("/api/v1/attempts/" + student.attemptId() + "/answers", Map.of("wrong", 1), student.token()),
                400, "VALIDATION_ERROR");
    }

    @Test
    void invalidConstraintsAreRejectedWithFieldErrors() throws Exception {
        Student student = startStudent();
        Map<String, Object> bad = Map.of(
                "gpa", Map.of("toan", 11),
                "combos", List.of("abc"),
                "familyPressure", 6,
                "budgetPerYear", -1);

        MvcResult result = put("/api/v1/attempts/" + student.attemptId() + "/constraints", bad, student.token());

        assertError(result, 400, "VALIDATION_ERROR");
        assertThat(json(result).get("error").get("details")).hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    void savingConstraintsAgainReplacesThePreviousOnes() throws Exception {
        Student student = startStudent();
        String path = "/api/v1/attempts/" + student.attemptId() + "/constraints";
        put(path, validConstraints(), student.token());

        MvcResult second = put(path, Map.of("familyPressure", 1), student.token());

        assertThat(second.getResponse().getStatus()).isEqualTo(200);
        JsonNode saved = json(second).get("data");
        assertThat(saved.get("familyPressure").asInt()).isEqualTo(1);
        assertThat(saved.get("gpa").isNull()).isTrue();
        assertThat(saved.get("combos").isNull()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from student_constraints where attempt_id = ?::uuid",
                Integer.class, student.attemptId())).isEqualTo(1);
    }

    // ---------- access control ----------

    @Test
    void nobodyCanReadOrChangeSomeoneElsesAttempt() throws Exception {
        Student owner = startStudent();
        Student other = startStudent();
        String questionId = activeSurvey(owner.token()).get("sections").get(0).get("questions").get(0).get("id").asText();
        saveAnswers(owner, singleAnswer(questionId, 4));
        String path = "/api/v1/attempts/" + owner.attemptId();

        assertError(get(path, other.token()), 403, "ACCESS_DENIED");
        assertError(put(path + "/answers", Map.of("answers", singleAnswer(questionId, 1)), other.token()),
                403, "ACCESS_DENIED");
        assertError(put(path + "/constraints", validConstraints(), other.token()), 403, "ACCESS_DENIED");

        // and the owner's data is untouched
        JsonNode detail = json(get(path, owner.token())).get("data");
        assertThat(detail.get("answers").get(0).get("value").asInt()).isEqualTo(4);
        assertThat(detail.get("constraints").isNull()).isTrue();
    }

    @Test
    void parentsAndAdminsCannotUseTheStudentAttemptEndpoints() throws Exception {
        Student owner = startStudent();
        String path = "/api/v1/attempts/" + owner.attemptId();
        String parent = registerAndLogin("PARENT", null).get("accessToken").asText();

        assertError(get(path, parent), 403, "ACCESS_DENIED");
        assertError(get(path, adminToken()), 403, "ACCESS_DENIED");
        assertError(get(path, null), 401, "UNAUTHORIZED");
    }

    @Test
    void unknownAttemptIsNotFound() throws Exception {
        assertError(get("/api/v1/attempts/" + UUID.randomUUID(), studentToken()), 404, "ATTEMPT_NOT_FOUND");
    }

    // ---------- lifecycle / history ----------

    @Test
    void aSubmittedAttemptCanNoLongerBeEditedAndTheStudentCanStartANewOne() throws Exception {
        Student student = startStudent();
        String questionId = activeSurvey(student.token()).get("sections").get(0).get("questions").get(0).get("id").asText();
        jdbc.update("update survey_attempts set status = 'SUBMITTED', submitted_at = now() where id = ?::uuid",
                student.attemptId());

        assertError(saveAnswers(student, singleAnswer(questionId, 3)), 422, "ATTEMPT_NOT_EDITABLE");
        assertError(put("/api/v1/attempts/" + student.attemptId() + "/constraints", validConstraints(), student.token()),
                422, "ATTEMPT_NOT_EDITABLE");

        MvcResult next = post("/api/v1/attempts", null, student.token());
        assertThat(next.getResponse().getStatus()).isEqualTo(201);
        assertThat(json(next).get("data").get("id").asText()).isNotEqualTo(student.attemptId());
    }

    @Test
    void historyListsOnlyMyAttemptsNewestFirstWithPaging() throws Exception {
        Student student = startStudent();
        jdbc.update("update survey_attempts set status = 'SCORED' where id = ?::uuid", student.attemptId());
        post("/api/v1/attempts", null, student.token());
        startStudent();   // somebody else's attempt must not show up

        JsonNode page = json(get("/api/v1/me/attempts", student.token())).get("data");

        assertThat(page.get("totalItems").asInt()).isEqualTo(2);
        assertThat(page.get("items")).hasSize(2);
        assertThat(page.get("items").get(0).get("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(page.get("items").get(1).get("status").asText()).isEqualTo("SCORED");
        assertThat(page.get("items").get(0).get("surveyVersion").asInt()).isPositive();

        JsonNode secondPage = json(get("/api/v1/me/attempts?page=1&size=1", student.token())).get("data");
        assertThat(secondPage.get("items")).hasSize(1);
        assertThat(secondPage.get("size").asInt()).isEqualTo(1);
        assertThat(secondPage.get("totalPages").asInt()).isEqualTo(2);
    }

    @Test
    void pageSizeIsCappedAtOneHundred() throws Exception {
        JsonNode page = json(get("/api/v1/me/attempts?size=1000", studentToken())).get("data");

        assertThat(page.get("size").asInt()).isEqualTo(100);
    }
}

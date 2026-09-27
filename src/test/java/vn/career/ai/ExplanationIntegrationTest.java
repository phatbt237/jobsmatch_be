package vn.career.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import vn.career.ai.infrastructure.FakeLlmGateway;
import vn.career.support.AbstractStudentFlowTest;

/** The asynchronous part of submit: explanations written by the LLM after the response has been sent. */
class ExplanationIntegrationTest extends AbstractStudentFlowTest {

    @Autowired
    private FakeLlmGateway fake;

    private void awaitStatus(Student student, String status) {
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100)).untilAsserted(() ->
                assertThat(result(student).get("explanationStatus").asText()).isEqualTo(status));
    }

    private UUID attemptId(Student student) {
        return UUID.fromString(student.attemptId());
    }

    // ---------- happy path ----------

    @Test
    void explanationsAppearAfterSubmitWithoutBlockingTheResponse() throws Exception {
        Student student = readyStudent();
        fake.delay(attemptId(student), Duration.ofSeconds(2));

        long start = System.nanoTime();
        JsonNode submitted = submit(student);
        long millis = (System.nanoTime() - start) / 1_000_000;

        // the response did not wait for the (2 second) LLM call
        assertThat(millis).isLessThan(1500);
        assertThat(submitted.get("explanationStatus").asText()).isEqualTo("PENDING");
        assertThat(submitted.get("summary").isNull()).isTrue();
        for (JsonNode rec : submitted.get("recommendations")) {
            assertThat(rec.get("explanation").isNull()).isTrue();
            assertThat(rec.get("explanationStatus").asText()).isEqualTo("PENDING");
        }
        // the result endpoint works while the explanation is still pending
        assertThat(result(student).get("recommendations")).hasSize(5);

        awaitStatus(student, "DONE");
        JsonNode done = result(student);
        assertThat(done.get("summary").asText()).isNotBlank();
        for (JsonNode rec : done.get("recommendations")) {
            assertThat(rec.get("explanation").asText()).contains(rec.get("majorName").asText());
            assertThat(rec.get("explanationStatus").asText()).isEqualTo("DONE");
        }
        assertThat(jdbc.queryForObject("select count(*) from recommendations where attempt_id = ?::uuid and llm_model = 'fake-llm' "
                + "and explanation_status = 'DONE'", Integer.class, student.attemptId())).isEqualTo(5);
        assertThat(jdbc.queryForObject("select count(*) from recommendation_summaries where attempt_id = ?::uuid",
                Integer.class, student.attemptId())).isEqualTo(1);
        assertThat(fake.explanationCalls(attemptId(student))).isEqualTo(1);
    }

    @Test
    void theExplanationDoesNotChangeTheRankingOrScores() throws Exception {
        Student student = readyStudent();
        fake.delay(attemptId(student), Duration.ofMillis(500));
        JsonNode before = submit(student);
        awaitStatus(student, "DONE");
        JsonNode after = result(student);

        for (int i = 0; i < 5; i++) {
            assertThat(after.get("recommendations").get(i).get("majorId")).isEqualTo(before.get("recommendations").get(i).get("majorId"));
            assertThat(after.get("recommendations").get(i).get("score")).isEqualTo(before.get("recommendations").get(i).get("score"));
        }
    }

    // ---------- failures ----------

    @Test
    void whenTheLlmKeepsFailingTheStatusIsFailedAndTheResultStillWorks() throws Exception {
        Student student = readyStudent();
        fake.failAlways(attemptId(student));

        submit(student);
        awaitStatus(student, "FAILED");

        JsonNode failed = result(student);
        assertThat(failed.get("recommendations")).hasSize(5);
        assertThat(failed.get("summary").isNull()).isTrue();
        for (JsonNode rec : failed.get("recommendations")) {
            assertThat(rec.get("explanation").isNull()).isTrue();
            assertThat(rec.get("explanationStatus").asText()).isEqualTo("FAILED");
            assertThat(rec.get("breakdown").get("final").asDouble()).isEqualTo(rec.get("score").asDouble(),
                    org.assertj.core.data.Offset.offset(0.0001));
        }
        // one try plus two retries
        assertThat(fake.explanationCalls(attemptId(student))).isEqualTo(3);
        assertThat(failed.get("dimensions")).hasSize(19);
    }

    @Test
    void transientFailuresAreRetriedUntilItWorks() throws Exception {
        Student student = readyStudent();
        fake.failNext(attemptId(student), 2);

        submit(student);
        awaitStatus(student, "DONE");

        assertThat(fake.explanationCalls(attemptId(student))).isEqualTo(3);
        assertThat(result(student).get("summary").asText()).isNotBlank();
    }

    @Test
    void aReplyThatIsNotJsonIsTreatedAsAFailedTryAndRetried() throws Exception {
        Student student = readyStudent();
        fake.replyWithRawOnce(attemptId(student), "Xin chào! Đây là lời giải thích nhưng không phải JSON.");

        submit(student);
        awaitStatus(student, "DONE");

        assertThat(fake.explanationCalls(attemptId(student))).isEqualTo(2);
    }

    @Test
    void anIncompleteSubmitNeverCallsTheLlm() throws Exception {
        Student student = startedStudent(null, null);

        assertError(post("/api/v1/attempts/" + student.attemptId() + "/submit", null, student.token()), 422, "SURVEY_INCOMPLETE");
        Thread.sleep(300);

        assertThat(fake.explanationCalls(attemptId(student))).isZero();
    }

    // ---------- server-sent events ----------

    @Test
    void theStreamStaysOpenUntilTheExplanationIsReadyThenDeliversTheResult() throws Exception {
        Student student = readyStudent();
        fake.delay(attemptId(student), Duration.ofSeconds(2));
        submit(student);

        MvcResult stream = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/attempts/" + student.attemptId() + "/result/stream")
                .header("Authorization", "Bearer " + student.token())
                .accept(MediaType.TEXT_EVENT_STREAM)).andReturn();

        assertThat(stream.getRequest().isAsyncStarted()).as("connection is kept open while the LLM works").isTrue();
        assertThat(stream.getResponse().getContentAsString()).doesNotContain("explanation");

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(stream.getResponse().getContentAsString()).contains("event:explanation"));
        String body = stream.getResponse().getContentAsString();
        String payload = body.lines().filter(l -> l.startsWith("data:")).findFirst().orElseThrow().substring(5);
        JsonNode event = objectMapper.readTree(payload);
        assertThat(event.get("explanationStatus").asText()).isEqualTo("DONE");
        assertThat(event.get("recommendations").get(0).get("explanation").asText()).isNotBlank();
    }

    @Test
    void theStreamAlsoReportsAFailedExplanation() throws Exception {
        Student student = readyStudent();
        fake.failAlways(attemptId(student));
        fake.delay(attemptId(student), Duration.ofMillis(800));
        submit(student);

        MvcResult stream = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/attempts/" + student.attemptId() + "/result/stream")
                .header("Authorization", "Bearer " + student.token())).andReturn();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(stream.getResponse().getContentAsString()).contains("event:explanation").contains("FAILED"));
    }

    @Test
    void openingTheStreamAfterCompletionSendsTheEventImmediately() throws Exception {
        Student student = readyStudent();
        submit(student);
        awaitStatus(student, "DONE");

        MvcResult stream = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/attempts/" + student.attemptId() + "/result/stream")
                .header("Authorization", "Bearer " + student.token())).andReturn();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(stream.getResponse().getContentAsString()).contains("event:explanation").contains("DONE"));
    }

    @Test
    void theStreamNeedsAnAuthorisedCaller() throws Exception {
        Student student = readyStudent();
        submit(student);
        String path = "/api/v1/attempts/" + student.attemptId() + "/result/stream";

        assertError(get(path, null), 401, "UNAUTHORIZED");
        assertError(get(path, studentToken()), 403, "ACCESS_DENIED");
        assertError(get("/api/v1/attempts/" + UUID.randomUUID() + "/result/stream", student.token()), 404, "ATTEMPT_NOT_FOUND");
    }
}

package vn.career.survey;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import vn.career.support.AbstractIntegrationTest;

/** Checks the data created by V2_1__seed_survey.sql and what students are allowed to see of it. */
class SurveySeedIntegrationTest extends AbstractIntegrationTest {

    private static final String V1 = "(select id from surveys where version = 1)";

    @Autowired
    private JdbcTemplate jdbc;

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void hasNineteenDimensionsInThreeGroups() {
        assertThat(count("select count(*) from dimensions")).isEqualTo(19);
        assertThat(count("select count(*) from dimensions where group_code = 'INTEREST'")).isEqualTo(6);
        assertThat(count("select count(*) from dimensions where group_code = 'APTITUDE'")).isEqualTo(6);
        assertThat(count("select count(*) from dimensions where group_code = 'VALUE'")).isEqualTo(7);
    }

    @Test
    void versionOneIsPublishedWithFourSections() {
        assertThat(jdbc.queryForObject("select status from surveys where version = 1", String.class))
                .isEqualTo("PUBLISHED");
        List<Map<String, Object>> sections = jdbc.queryForList(
                "select s.code, count(q.id) as questions from survey_sections s "
                        + "left join questions q on q.section_id = s.id "
                        + "where s.survey_id = " + V1 + " group by s.code, s.order_index order by s.order_index");
        assertThat(sections).extracting(row -> row.get("code")).containsExactly("A", "B", "C", "D");
        assertThat(sections).extracting(row -> ((Number) row.get("questions")).intValue())
                .containsExactly(37, 18, 15, 0);
    }

    @Test
    void sectionAHasSixStatementsPerRiasecDimensionWithExactlyOneReversed() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select dimension_code, count(*) as total, count(*) filter (where reverse_scored) as reversed "
                        + "from questions q join survey_sections s on s.id = q.section_id "
                        + "where s.survey_id = " + V1 + " and s.code = 'A' and q.dimension_code is not null "
                        + "group by dimension_code");
        assertThat(rows).hasSize(6);
        assertThat(rows).allSatisfy(row -> {
            assertThat(((Number) row.get("total")).intValue()).isEqualTo(6);
            assertThat(((Number) row.get("reversed")).intValue()).isEqualTo(1);
        });
        assertThat(rows).extracting(row -> row.get("dimension_code"))
                .containsExactlyInAnyOrder("R", "I", "A", "S", "E", "C");
    }

    @Test
    void sectionBHasTwelveSelfRatingsAndSixMiniTestsWithOneCorrectOptionEach() {
        assertThat(count("select count(*) from questions q join survey_sections s on s.id = q.section_id "
                + "where s.survey_id = " + V1 + " and s.code = 'B' and q.type = 'LIKERT'")).isEqualTo(12);
        assertThat(count("select count(*) from questions q join survey_sections s on s.id = q.section_id "
                + "where s.survey_id = " + V1 + " and s.code = 'B' and q.type = 'MINI_TEST'")).isEqualTo(6);
        // every mini-test: 4 options, exactly one correct
        assertThat(count("select count(*) from (select q.id from questions q join question_options o on o.question_id = q.id "
                + "where q.type = 'MINI_TEST' group by q.id having count(*) = 4 and count(*) filter (where o.is_correct) = 1) t"))
                .isEqualTo(6);
    }

    @Test
    void sectionCHasFourteenValueStatementsAcrossSevenValues() {
        assertThat(count("select count(*) from questions q join survey_sections s on s.id = q.section_id "
                + "where s.survey_id = " + V1 + " and s.code = 'C' and q.dimension_code like 'VAL\\_%'")).isEqualTo(14);
        assertThat(count("select count(distinct q.dimension_code) from questions q join survey_sections s on s.id = q.section_id "
                + "where s.survey_id = " + V1 + " and s.code = 'C'")).isEqualTo(7);
    }

    @Test
    void hasTwoAttentionChecksWithoutDimension() {
        List<Map<String, Object>> checks = jdbc.queryForList(
                "select q.expected_value::text as expected, q.dimension_code from questions q "
                        + "join survey_sections s on s.id = q.section_id where s.survey_id = " + V1
                        + " and q.is_attention_check order by q.expected_value::text");
        assertThat(checks).hasSize(2);
        assertThat(checks).extracting(row -> row.get("expected")).containsExactly("1", "4");
        assertThat(checks).extracting(row -> row.get("dimension_code")).containsOnlyNulls();
    }

    @Test
    void seededQuestionsPassTheSameRulesAdminsAreHeldTo() {
        // LIKERT questions never have options; every scored question has a dimension
        assertThat(count("select count(*) from questions q join question_options o on o.question_id = q.id "
                + "where q.type = 'LIKERT'")).isZero();
        assertThat(count("select count(*) from questions where dimension_code is null and not is_attention_check")).isZero();
    }

    // ---------- what students can see ----------

    @Test
    void studentsSeeTheSurveyWithoutAnyScoringOrAnswerKeyData() throws Exception {
        MvcResult result = get("/api/v1/surveys/active", studentToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("expectedValue", "correct", "dimension", "weight", "reverse", "attention");

        JsonNode survey = json(result).get("data");
        assertThat(survey.get("sections")).hasSize(4);
        assertThat(survey.get("likertScale")).hasSize(5);
        int questions = 0;
        for (JsonNode section : survey.get("sections")) {
            questions += section.get("questions").size();
        }
        assertThat(questions).isGreaterThan(0);
    }

    @Test
    void miniTestOptionsAreExposedOnlyAsIdOrderAndLabel() throws Exception {
        JsonNode survey = json(get("/api/v1/surveys/active", studentToken())).get("data");

        JsonNode miniTest = null;
        for (JsonNode section : survey.get("sections")) {
            for (JsonNode question : section.get("questions")) {
                if ("MINI_TEST".equals(question.get("type").asText())) {
                    miniTest = question;
                }
            }
        }
        assertThat(miniTest).isNotNull();
        JsonNode option = miniTest.get("options").get(0);
        assertThat(option.fieldNames()).toIterable().containsExactlyInAnyOrder("id", "orderIndex", "label");
    }

    @Test
    void surveyRequiresAuthentication() throws Exception {
        assertThat(get("/api/v1/surveys/active", null).getResponse().getStatus()).isEqualTo(401);
    }
}

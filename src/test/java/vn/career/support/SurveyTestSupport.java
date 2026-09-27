package vn.career.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Builds complete, valid answer sets for the seeded survey from its database definition, so tests can describe a
 * student ("high I and C, low A") instead of writing 70 answers by hand.
 */
public class SurveyTestSupport {

    /**
     * How a test student answers. {@code targets} maps a dimension code to the score (1..5) the student should end up
     * with: reverse scored statements are answered mirrored so the scored value matches. Unlisted dimensions use
     * {@code defaultTarget}.
     */
    public record Persona(Map<String, Integer> targets, int defaultTarget, boolean correctMiniTests, boolean passAttention) {

        public static Persona of(int defaultTarget, Object... dimensionTargets) {
            Map<String, Integer> map = new HashMap<>();
            for (int i = 0; i < dimensionTargets.length; i += 2) {
                map.put((String) dimensionTargets[i], (Integer) dimensionTargets[i + 1]);
            }
            return new Persona(map, defaultTarget, true, true);
        }

        public Persona withAttentionFailing() {
            return new Persona(targets, defaultTarget, correctMiniTests, false);
        }

        public Persona withWrongMiniTests() {
            return new Persona(targets, defaultTarget, false, passAttention);
        }
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public SurveyTestSupport(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /** One valid answer for every question of the given survey version. */
    public ArrayNode answers(int surveyVersion, Persona persona) {
        ArrayNode answers = objectMapper.createArrayNode();
        List<Map<String, Object>> questions = jdbc.queryForList(
                "select q.id::text as id, q.type, q.dimension_code, q.reverse_scored, q.is_attention_check, "
                        + "q.expected_value::text as expected from questions q "
                        + "join survey_sections s on s.id = q.section_id join surveys sv on sv.id = s.survey_id "
                        + "where sv.version = ? order by s.order_index, q.order_index", surveyVersion);
        for (Map<String, Object> q : questions) {
            ObjectNode item = answers.addObject();
            item.put("questionId", (String) q.get("id"));
            String type = (String) q.get("type");
            switch (type) {
                case "LIKERT" -> item.put("value", likertValue(q, persona));
                case "MINI_TEST" -> item.put("value", miniTestOption((String) q.get("id"), persona.correctMiniTests()));
                case "SINGLE_CHOICE" -> item.put("value", anyOption((String) q.get("id")));
                case "NUMERIC_INPUT" -> item.put("value", 1);
                default -> throw new IllegalStateException("Unsupported question type in test data: " + type);
            }
        }
        return answers;
    }

    /** Every Likert answer is the same value, to provoke the straight-lining flag. */
    public ArrayNode straightLined(int surveyVersion, int value) {
        ArrayNode answers = answers(surveyVersion, Persona.of(3));
        List<Map<String, Object>> likert = jdbc.queryForList(
                "select q.id::text as id, q.is_attention_check from questions q "
                        + "join survey_sections s on s.id = q.section_id join surveys sv on sv.id = s.survey_id "
                        + "where sv.version = ? and q.type = 'LIKERT'", surveyVersion);
        for (var node : answers) {
            ObjectNode item = (ObjectNode) node;
            boolean isLikertNoCheck = likert.stream().anyMatch(l -> l.get("id").equals(item.get("questionId").asText())
                    && !(Boolean) l.get("is_attention_check"));
            if (isLikertNoCheck) {
                item.put("value", value);
            }
        }
        return answers;
    }

    private int likertValue(Map<String, Object> q, Persona persona) {
        if ((Boolean) q.get("is_attention_check")) {
            int expected = Integer.parseInt((String) q.get("expected"));
            return persona.passAttention() ? expected : (expected == 3 ? 5 : 3);
        }
        int target = persona.targets().getOrDefault((String) q.get("dimension_code"), persona.defaultTarget());
        return (Boolean) q.get("reverse_scored") ? 6 - target : target;
    }

    private String miniTestOption(String questionId, boolean correct) {
        return jdbc.queryForObject("select id::text from question_options where question_id = ?::uuid "
                + "and is_correct = ? order by order_index limit 1", String.class, questionId, correct);
    }

    private String anyOption(String questionId) {
        return jdbc.queryForObject("select id::text from question_options where question_id = ?::uuid "
                + "order by order_index limit 1", String.class, questionId);
    }
}

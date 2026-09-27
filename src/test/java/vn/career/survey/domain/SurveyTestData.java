package vn.career.survey.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;

/** Builders for in-memory survey objects. Ids are normally created by Hibernate, so tests set them directly. */
public final class SurveyTestData {

    public static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private SurveyTestData() {
    }

    public static QuestionSpec spec(QuestionType type, String dimension, List<QuestionSpec.OptionSpec> options) {
        return new QuestionSpec(type, "content", dimension, BigDecimal.ONE, false, false, null, true, 1, options);
    }

    public static QuestionSpec spec(QuestionType type, String dimension) {
        return spec(type, dimension, List.of());
    }

    public static QuestionSpec.OptionSpec option(String label, BigDecimal value, boolean correct) {
        return new QuestionSpec.OptionSpec(label, value, correct);
    }

    public static List<QuestionSpec.OptionSpec> plainOptions(int count) {
        List<QuestionSpec.OptionSpec> options = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            options.add(option("option " + i, BigDecimal.valueOf(i), false));
        }
        return options;
    }

    /** A question inside a fresh DRAFT survey, with generated ids on the question and its options. */
    public static Question question(QuestionSpec spec) {
        Survey survey = Survey.draft(1, "test");
        SurveySection section = survey.addSection(1, "A", "Section A", null);
        Question question = section.addQuestion(spec);
        assignIds(question);
        return question;
    }

    public static void assignIds(Question question) {
        ReflectionTestUtils.setField(question, "id", UUID.randomUUID());
        question.getOptions().forEach(option -> ReflectionTestUtils.setField(option, "id", UUID.randomUUID()));
    }

    public static JsonNode number(int value) {
        return JSON.numberNode(value);
    }

    public static JsonNode text(String value) {
        return JSON.textNode(value);
    }

    public static JsonNode array(String... values) {
        var array = JSON.arrayNode();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }
}

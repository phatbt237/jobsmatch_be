package vn.career.survey.domain;

public enum QuestionType {
    /** Answer: integer 1..5. */
    LIKERT,
    /** Answer: array with every option id, best first. */
    RANKING,
    /** Answer: one option id. */
    SINGLE_CHOICE,
    /** Answer: one option id, exactly one option is correct. */
    MINI_TEST,
    /** Answer: a number. */
    NUMERIC_INPUT
}

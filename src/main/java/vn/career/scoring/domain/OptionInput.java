package vn.career.scoring.domain;

/** An answer option as the scorer sees it. {@code value} may be null (only SINGLE_CHOICE options carry a score). */
public record OptionInput(String id, int orderIndex, Double value, boolean correct) {
}

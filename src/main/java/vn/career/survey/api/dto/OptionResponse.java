package vn.career.survey.api.dto;

import java.util.UUID;

/** Option as seen by a student: no correctness flag and no score. */
public record OptionResponse(UUID id, int orderIndex, String label) {
}

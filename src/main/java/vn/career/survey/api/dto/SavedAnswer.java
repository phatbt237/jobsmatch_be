package vn.career.survey.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record SavedAnswer(UUID questionId, JsonNode value, Instant answeredAt) {
}

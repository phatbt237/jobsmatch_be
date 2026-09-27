package vn.career.survey.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AdminOptionResponse(UUID id, int orderIndex, String label, BigDecimal value, boolean correct) {
}

package vn.career.survey.api.dto;

import vn.career.survey.domain.DimensionGroup;

public record DimensionResponse(String code, String name, DimensionGroup group) {
}

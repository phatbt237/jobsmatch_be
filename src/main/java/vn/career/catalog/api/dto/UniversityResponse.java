package vn.career.catalog.api.dto;

import java.util.UUID;

public record UniversityResponse(UUID id, String code, String name, String region, String type, boolean sampleData) {
}

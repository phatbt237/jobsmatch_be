package vn.career.ai.api.dto;

import java.util.UUID;

/** A source the answer is based on. {@code type} is POV (a mentor's post) or MAJOR. */
public record SourceRef(String type, UUID id, String title) {
}

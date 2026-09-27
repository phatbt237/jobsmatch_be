package vn.career.catalog.application;

import java.util.UUID;

/** Published after a major (or its profile / admission data) changed, so search indexes can be refreshed. */
public record MajorChangedEvent(UUID majorId) {
}

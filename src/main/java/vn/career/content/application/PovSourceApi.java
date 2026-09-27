package vn.career.content.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Published posts as plain text, for the search index of the AI module. */
public interface PovSourceApi {

    /** The post as text, or empty when it does not exist or is not PUBLISHED. */
    Optional<PovDocument> loadPublished(UUID povId);

    List<UUID> publishedPovIds();

    /** {@code text} contains the title and all sections. Author details are limited to job title and experience. */
    record PovDocument(UUID id, UUID majorId, String title, String text, String jobTitle, int yearsExperience) {
    }
}

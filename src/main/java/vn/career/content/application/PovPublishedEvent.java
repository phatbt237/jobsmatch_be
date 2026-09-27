package vn.career.content.application;

import java.util.UUID;

/** Published when an admin approves a post, so the AI module can (re)build its search index for it. */
public record PovPublishedEvent(UUID povId) {
}

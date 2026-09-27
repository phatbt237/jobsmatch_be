package vn.career.content.application;

import java.util.UUID;

/** Published after a post that was visible to readers has been deleted (its author erased their account). */
public record PovRemovedEvent(UUID povId) {
}

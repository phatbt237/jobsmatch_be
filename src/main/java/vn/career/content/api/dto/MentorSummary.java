package vn.career.content.api.dto;

/** What readers of a public post learn about its author: role and experience only, no name or employer. */
public record MentorSummary(String jobTitle, int yearsExperience) {
}

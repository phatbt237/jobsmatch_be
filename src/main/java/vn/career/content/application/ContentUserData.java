package vn.career.content.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.career.common.event.DomainEventPublisher;
import vn.career.common.userdata.UserDataEraser;
import vn.career.common.userdata.UserDataExporter;
import vn.career.content.domain.PovPost;
import vn.career.content.domain.PovStatus;
import vn.career.content.domain.QaAnswer;
import vn.career.content.domain.QaThread;
import vn.career.content.infrastructure.MentorRepository;
import vn.career.content.infrastructure.PovPostRepository;
import vn.career.content.infrastructure.QaAnswerRepository;
import vn.career.content.infrastructure.QaThreadRepository;

/**
 * Content part of "delete my account" and "export my data": mentor profile and posts, questions and answers the
 * user wrote. Deleting a published post announces {@link PovRemovedEvent} so the search index drops it.
 */
@Component
@RequiredArgsConstructor
class ContentUserData implements UserDataEraser, UserDataExporter {

    private final MentorRepository mentors;
    private final PovPostRepository posts;
    private final QaThreadRepository threads;
    private final QaAnswerRepository answers;
    private final DomainEventPublisher events;

    @Override
    @Transactional
    public void eraseUserData(UUID userId) {
        List<UUID> publishedPosts = posts.findByMentorId(userId).stream()
                .filter(p -> p.getStatus() == PovStatus.PUBLISHED).map(PovPost::getId).toList();
        // the mentor row cascades to the mentor's posts
        mentors.deleteById(userId);
        mentors.flush();
        publishedPosts.forEach(id -> events.publish(new PovRemovedEvent(id)));
        // answers first, then threads (their remaining answers cascade)
        answers.deleteAll(answers.findByAuthorId(userId));
        threads.deleteAll(threads.findByAuthorId(userId));
        threads.flush();
    }

    @Override
    public String section() {
        return "content";
    }

    @Override
    @Transactional(readOnly = true)
    public Object exportUserData(UUID userId) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("mentorProfile", mentors.findById(userId).map(m -> {
            Map<String, Object> profile = new LinkedHashMap<>();
            profile.put("company", m.getCompany());
            profile.put("jobTitle", m.getJobTitle());
            profile.put("yearsExperience", m.getYearsExperience());
            profile.put("bio", m.getBio());
            profile.put("linkedinUrl", m.getLinkedinUrl());
            profile.put("verified", m.isVerified());
            return profile;
        }).orElse(null));
        map.put("povPosts", posts.findByMentorId(userId).stream().map(ContentUserData::export).toList());
        map.put("questions", threads.findByAuthorId(userId).stream().map(ContentUserData::export).toList());
        map.put("answers", answers.findByAuthorId(userId).stream().map(ContentUserData::export).toList());
        return map;
    }

    private static Map<String, Object> export(PovPost post) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("title", post.getTitle());
        map.put("sections", post.getSections());
        map.put("videoUrl", post.getVideoUrl());
        map.put("status", post.getStatus());
        map.put("rejectReason", post.getRejectReason());
        map.put("createdAt", post.getCreatedAt());
        return map;
    }

    private static Map<String, Object> export(QaThread thread) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("title", thread.getTitle());
        map.put("content", thread.getContent());
        map.put("status", thread.getStatus());
        map.put("createdAt", thread.getCreatedAt());
        return map;
    }

    private static Map<String, Object> export(QaAnswer answer) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("content", answer.getContent());
        map.put("status", answer.getStatus());
        map.put("createdAt", answer.getCreatedAt());
        return map;
    }
}

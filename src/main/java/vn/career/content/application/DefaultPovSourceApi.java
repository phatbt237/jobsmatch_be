package vn.career.content.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.career.content.domain.PovPost;
import vn.career.content.domain.PovSections;
import vn.career.content.domain.PovStatus;
import vn.career.content.infrastructure.MentorRepository;
import vn.career.content.infrastructure.PovPostRepository;

@Service
@RequiredArgsConstructor
class DefaultPovSourceApi implements PovSourceApi {

    private final PovPostRepository posts;
    private final MentorRepository mentors;

    @Override
    @Transactional(readOnly = true)
    public Optional<PovDocument> loadPublished(UUID povId) {
        return posts.findByIdAndStatus(povId, PovStatus.PUBLISHED).map(this::toDocument);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> publishedPovIds() {
        return posts.findByStatus(PovStatus.PUBLISHED).stream().map(PovPost::getId).toList();
    }

    private PovDocument toDocument(PovPost post) {
        var mentor = mentors.findById(post.getMentorId());
        return new PovDocument(post.getId(), post.getMajorId(), post.getTitle(),
                PovSections.toPlainText(post.getTitle(), post.getSections()),
                mentor.map(m -> m.getJobTitle()).orElse("người làm trong ngành"),
                mentor.map(m -> m.getYearsExperience()).orElse(0));
    }
}

package vn.career.content.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.career.catalog.application.CatalogQueryApi;
import vn.career.common.api.ErrorDetail;
import vn.career.common.api.PageResponse;
import vn.career.common.audit.AuditAction;
import vn.career.common.audit.AuditService;
import vn.career.common.event.DomainEventPublisher;
import vn.career.common.exception.AppException;
import vn.career.common.exception.ErrorCode;
import vn.career.common.exception.ForbiddenException;
import vn.career.common.exception.NotFoundException;
import vn.career.content.api.dto.AdminPovResponse;
import vn.career.content.api.dto.MentorSummary;
import vn.career.content.api.dto.MyPovResponse;
import vn.career.content.api.dto.PovRequest;
import vn.career.content.api.dto.PovResponse;
import vn.career.content.domain.Mentor;
import vn.career.content.domain.PovPost;
import vn.career.content.domain.PovSections;
import vn.career.content.domain.PovStatus;
import vn.career.content.infrastructure.MentorRepository;
import vn.career.content.infrastructure.PovPostRepository;

/** POV posts: mentors write and submit, admins review, everybody reads the published ones. */
@Service
@RequiredArgsConstructor
public class PovService {

    private final PovPostRepository posts;
    private final MentorRepository mentors;
    private final CatalogQueryApi catalog;
    private final AuditService auditService;
    private final DomainEventPublisher events;
    private final Clock clock;

    // ---------------------------------------------------------------- mentor side

    @Transactional
    public MyPovResponse create(UUID mentorId, PovRequest request) {
        requireVerifiedMentor(mentorId);
        requireMajor(request.majorId());
        Map<String, String> sections = cleanSections(request.sections());
        PovPost post = posts.saveAndFlush(PovPost.draft(mentorId, request.majorId(), request.title().trim(), sections,
                blankToNull(request.videoUrl())));
        return toMine(post);
    }

    @Transactional
    public MyPovResponse update(UUID mentorId, UUID postId, PovRequest request) {
        PovPost post = findOwned(mentorId, postId);
        requireMajor(request.majorId());
        post.edit(request.majorId(), request.title().trim(), cleanSections(request.sections()), blankToNull(request.videoUrl()));
        posts.flush();
        return toMine(post);
    }

    @Transactional
    public MyPovResponse submit(UUID mentorId, UUID postId) {
        PovPost post = findOwned(mentorId, postId);
        List<ErrorDetail> missing = PovSections.validateForSubmit(post.getSections());
        if (post.isEditable() && !missing.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Fill in every section before submitting for review", missing);
        }
        post.submitForReview();
        posts.flush();
        return toMine(post);
    }

    @Transactional(readOnly = true)
    public PageResponse<MyPovResponse> mine(UUID mentorId, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "updatedAt"));
        return PageResponse.from(posts.findByMentorId(mentorId, sorted).map(PovService::toMine));
    }

    // ---------------------------------------------------------------- public reading

    @Transactional(readOnly = true)
    public PageResponse<PovResponse> publishedForMajor(UUID majorId, Pageable pageable) {
        if (!catalog.majorExists(majorId)) {
            throw new NotFoundException(ErrorCode.MAJOR_NOT_FOUND, "Major not found");
        }
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "publishedAt"));
        Page<PovPost> page = posts.findByMajorIdAndStatus(majorId, PovStatus.PUBLISHED, sorted);
        Map<UUID, Mentor> authors = authors(page.getContent().stream().map(PovPost::getMentorId).toList());
        return PageResponse.from(page.map(p -> toPublic(p, authors.get(p.getMentorId()))));
    }

    @Transactional(readOnly = true)
    public PovResponse published(UUID postId) {
        PovPost post = posts.findByIdAndStatus(postId, PovStatus.PUBLISHED)
                .orElseThrow(() -> new NotFoundException(ErrorCode.POV_NOT_FOUND, "Post not found"));
        return toPublic(post, mentors.findById(post.getMentorId()).orElse(null));
    }

    // ---------------------------------------------------------------- admin review

    @Transactional(readOnly = true)
    public PageResponse<AdminPovResponse> listForReview(PovStatus status, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.ASC, "updatedAt"));
        Page<PovPost> page = status == null ? posts.findAll(sorted) : posts.findByStatus(status, sorted);
        Map<UUID, Mentor> authors = authors(page.getContent().stream().map(PovPost::getMentorId).toList());
        return PageResponse.from(page.map(p -> toAdmin(p, authors.get(p.getMentorId()))));
    }

    /** Approves or rejects a post that is waiting for review. A rejection needs a reason. */
    @Transactional
    public AdminPovResponse review(UUID adminId, UUID postId, PovStatus decision, String reason) {
        PovPost post = posts.findById(postId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.POV_NOT_FOUND, "Post not found"));
        if (decision == PovStatus.REJECTED) {
            if (reason == null || reason.isBlank()) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "Request validation failed",
                        List.of(new ErrorDetail("reason", "is required when rejecting a post")));
            }
            post.reject(reason.trim());
        } else if (decision == PovStatus.PUBLISHED) {
            post.publish(Instant.now(clock));
        } else {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Request validation failed",
                    List.of(new ErrorDetail("status", "must be PUBLISHED or REJECTED")));
        }
        posts.flush();
        auditService.record(AuditAction.POV_REVIEWED, "PovPost", postId, Map.of("decision", decision.name()));
        if (decision == PovStatus.PUBLISHED) {
            events.publish(new PovPublishedEvent(postId));
        }
        return toAdmin(post, mentors.findById(post.getMentorId()).orElse(null));
    }

    // ---------------------------------------------------------------- helpers

    private Mentor requireVerifiedMentor(UUID userId) {
        Mentor mentor = mentors.findById(userId)
                .orElseThrow(() -> new ForbiddenException(ErrorCode.MENTOR_NOT_VERIFIED, "Apply to become a mentor first"));
        if (!mentor.isVerified()) {
            throw new ForbiddenException(ErrorCode.MENTOR_NOT_VERIFIED, "Your mentor profile has not been verified yet");
        }
        return mentor;
    }

    private void requireMajor(UUID majorId) {
        if (!catalog.majorExists(majorId)) {
            throw new NotFoundException(ErrorCode.MAJOR_NOT_FOUND, "Major not found");
        }
    }

    private PovPost findOwned(UUID mentorId, UUID postId) {
        PovPost post = posts.findById(postId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.POV_NOT_FOUND, "Post not found"));
        if (!post.getMentorId().equals(mentorId)) {
            throw new ForbiddenException("This post belongs to another mentor");
        }
        return post;
    }

    private Map<UUID, Mentor> authors(Collection<UUID> ids) {
        return mentors.findAllById(ids).stream().collect(Collectors.toMap(Mentor::getUserId, m -> m));
    }

    /** Trims values, drops empty ones and rejects unknown keys or over-long text. */
    private static Map<String, String> cleanSections(Map<String, String> raw) {
        List<ErrorDetail> problems = new ArrayList<>(PovSections.validateForSave(raw));
        if (!problems.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Request validation failed", problems);
        }
        Map<String, String> cleaned = new LinkedHashMap<>();
        PovSections.HEADINGS.keySet().forEach(key -> {
            String value = raw.get(key);
            if (value != null && !value.isBlank()) {
                cleaned.put(key, value.trim());
            }
        });
        return cleaned;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static MyPovResponse toMine(PovPost p) {
        return new MyPovResponse(p.getId(), p.getMajorId(), p.getTitle(), p.getSections(), p.getVideoUrl(), p.getStatus(),
                p.getRejectReason(), p.getPublishedAt(), p.getCreatedAt(), p.getUpdatedAt());
    }

    private static PovResponse toPublic(PovPost p, Mentor mentor) {
        return new PovResponse(p.getId(), p.getMajorId(), p.getTitle(), p.getSections(), p.getVideoUrl(),
                p.getPublishedAt(), summary(mentor), p.isSample());
    }

    private static AdminPovResponse toAdmin(PovPost p, Mentor mentor) {
        return new AdminPovResponse(p.getId(), p.getMentorId(), summary(mentor), mentor != null && mentor.isVerified(),
                p.getMajorId(), p.getTitle(), p.getSections(), p.getVideoUrl(), p.getStatus(), p.getRejectReason(),
                p.getPublishedAt(), p.getUpdatedAt(), p.isSample());
    }

    private static MentorSummary summary(Mentor mentor) {
        return mentor == null ? null : new MentorSummary(mentor.getJobTitle(), mentor.getYearsExperience());
    }
}

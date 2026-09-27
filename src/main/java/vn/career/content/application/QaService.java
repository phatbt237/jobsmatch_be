package vn.career.content.application;

import java.util.HashMap;
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
import vn.career.auth.application.AccountAccessApi;
import vn.career.auth.application.AccountRoleApi;
import vn.career.catalog.application.CatalogQueryApi;
import vn.career.common.api.PageResponse;
import vn.career.common.audit.AuditAction;
import vn.career.common.audit.AuditService;
import vn.career.common.exception.ErrorCode;
import vn.career.common.exception.ForbiddenException;
import vn.career.common.exception.NotFoundException;
import vn.career.common.security.AuthenticatedUser;
import vn.career.content.api.dto.QaAnswerRequest;
import vn.career.content.api.dto.QaAnswerResponse;
import vn.career.content.api.dto.QaThreadRequest;
import vn.career.content.api.dto.QaThreadResponse;
import vn.career.content.domain.ContentStatus;
import vn.career.content.domain.Mentor;
import vn.career.content.domain.QaAnswer;
import vn.career.content.domain.QaThread;
import vn.career.content.infrastructure.MentorRepository;
import vn.career.content.infrastructure.QaAnswerRepository;
import vn.career.content.infrastructure.QaThreadRepository;

/**
 * Community questions and answers per major. Students under the consent age cannot take part until a parent has
 * approved. Authors are shown by role only, and admins can hide content.
 */
@Service
@RequiredArgsConstructor
public class QaService {

    private final QaThreadRepository threads;
    private final QaAnswerRepository answers;
    private final MentorRepository mentors;
    private final CatalogQueryApi catalog;
    private final AccountAccessApi accountAccess;
    private final AccountRoleApi accountRoles;
    private final AuditService auditService;

    // ---------------------------------------------------------------- threads

    @Transactional(readOnly = true)
    public PageResponse<QaThreadResponse> listThreads(AuthenticatedUser caller, UUID majorId, Pageable pageable) {
        requireMayParticipate(caller);
        requireMajor(majorId);
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<QaThread> page = threads.findByMajorIdAndStatus(majorId, ContentStatus.VISIBLE, sorted);
        Map<UUID, Long> counts = new HashMap<>();
        if (!page.isEmpty()) {
            answers.countByThread(page.getContent().stream().map(QaThread::getId).toList(), ContentStatus.VISIBLE)
                    .forEach(row -> counts.put((UUID) row[0], (Long) row[1]));
        }
        Authors authors = authors(page.getContent().stream().map(QaThread::getAuthorId).toList());
        return PageResponse.from(page.map(t -> new QaThreadResponse(t.getId(), t.getMajorId(), t.getTitle(), t.getContent(),
                authors.role(t.getAuthorId()), authors.label(t.getAuthorId()), t.getCreatedAt(), counts.getOrDefault(t.getId(), 0L))));
    }

    @Transactional
    public QaThreadResponse ask(AuthenticatedUser caller, UUID majorId, QaThreadRequest request) {
        requireMayParticipate(caller);
        requireMajor(majorId);
        QaThread thread = threads.saveAndFlush(QaThread.ask(caller.id(), majorId, request.title().trim(), request.content().trim()));
        Authors authors = authors(List.of(caller.id()));
        return new QaThreadResponse(thread.getId(), majorId, thread.getTitle(), thread.getContent(),
                authors.role(caller.id()), authors.label(caller.id()), thread.getCreatedAt(), 0);
    }

    // ---------------------------------------------------------------- answers

    @Transactional(readOnly = true)
    public PageResponse<QaAnswerResponse> listAnswers(AuthenticatedUser caller, UUID threadId, Pageable pageable) {
        requireMayParticipate(caller);
        requireVisibleThread(threadId);
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.ASC, "createdAt"));
        Page<QaAnswer> page = answers.findByThreadIdAndStatus(threadId, ContentStatus.VISIBLE, sorted);
        Authors authors = authors(page.getContent().stream().map(QaAnswer::getAuthorId).toList());
        return PageResponse.from(page.map(a -> toResponse(a, authors)));
    }

    @Transactional
    public QaAnswerResponse answer(AuthenticatedUser caller, UUID threadId, QaAnswerRequest request) {
        requireMayParticipate(caller);
        requireVisibleThread(threadId);
        boolean verifiedMentor = "MENTOR".equals(caller.role())
                && mentors.findById(caller.id()).map(Mentor::isVerified).orElse(false);
        QaAnswer answer = answers.saveAndFlush(QaAnswer.reply(threadId, caller.id(), request.content().trim(), verifiedMentor));
        return toResponse(answer, authors(List.of(caller.id())));
    }

    // ---------------------------------------------------------------- moderation (admin)

    @Transactional
    public void moderateThread(UUID threadId, ContentStatus status) {
        QaThread thread = threads.findById(threadId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.THREAD_NOT_FOUND, "Thread not found"));
        thread.moderate(status);
        threads.flush();
        auditService.record(AuditAction.QA_MODERATED, "QaThread", threadId, Map.of("status", status.name()));
    }

    @Transactional
    public void moderateAnswer(UUID answerId, ContentStatus status) {
        QaAnswer answer = answers.findById(answerId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ANSWER_NOT_FOUND, "Answer not found"));
        answer.moderate(status);
        answers.flush();
        auditService.record(AuditAction.QA_MODERATED, "QaAnswer", answerId, Map.of("status", status.name()));
    }

    // ---------------------------------------------------------------- helpers

    /** Students need to be ACTIVE, that is any required parental consent has been given. */
    private void requireMayParticipate(AuthenticatedUser caller) {
        if ("STUDENT".equals(caller.role()) && !accountAccess.canUseAiFeatures(caller.id())) {
            throw new ForbiddenException(ErrorCode.CONSENT_REQUIRED,
                    "A parent has to approve your account before you can use community Q&A");
        }
    }

    private void requireMajor(UUID majorId) {
        if (!catalog.majorExists(majorId)) {
            throw new NotFoundException(ErrorCode.MAJOR_NOT_FOUND, "Major not found");
        }
    }

    private void requireVisibleThread(UUID threadId) {
        threads.findByIdAndStatus(threadId, ContentStatus.VISIBLE)
                .orElseThrow(() -> new NotFoundException(ErrorCode.THREAD_NOT_FOUND, "Thread not found"));
    }

    private QaAnswerResponse toResponse(QaAnswer a, Authors authors) {
        return new QaAnswerResponse(a.getId(), a.getThreadId(), a.getContent(), authors.role(a.getAuthorId()),
                authors.label(a.getAuthorId()), a.isMentorAnswer(), a.getCreatedAt());
    }

    /** Role and a privacy-friendly label for a set of authors. */
    private Authors authors(List<UUID> ids) {
        Map<UUID, String> roles = accountRoles.rolesOf(ids.stream().distinct().toList());
        Map<UUID, Mentor> mentorProfiles = mentors.findAllById(ids).stream().collect(Collectors.toMap(Mentor::getUserId, m -> m));
        return new Authors(roles, mentorProfiles);
    }

    private record Authors(Map<UUID, String> roles, Map<UUID, Mentor> mentors) {

        String role(UUID id) {
            return roles.getOrDefault(id, "UNKNOWN");
        }

        String label(UUID id) {
            return switch (role(id)) {
                case "STUDENT" -> "Học sinh";
                case "PARENT" -> "Phụ huynh";
                case "ADMIN" -> "Quản trị viên";
                case "MENTOR" -> mentors.containsKey(id) ? mentors.get(id).getJobTitle() + " (mentor)" : "Mentor";
                default -> "Người dùng";
            };
        }
    }
}

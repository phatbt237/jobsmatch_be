package vn.career.ai.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.career.ai.domain.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    /** Newest first; the caller passes the page size (how much history to keep). */
    List<ChatMessage> findByConversationIdAndUserIdOrderBySeqDesc(UUID conversationId, UUID userId, Pageable pageable);

    boolean existsByConversationIdAndUserId(UUID conversationId, UUID userId);

    List<ChatMessage> findByUserIdOrderBySeqAsc(UUID userId);

    void deleteByUserId(UUID userId);
}

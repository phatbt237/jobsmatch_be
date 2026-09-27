package vn.career.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.career.common.domain.BaseEntity;

/** One message of a chat conversation. Personal data: never logged, erased with the account. */
@Entity
@Table(name = "chat_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage extends BaseEntity {

    public enum Role {
        USER,
        ASSISTANT
    }

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Role role;

    @Column(nullable = false, updatable = false)
    private String content;

    /** Sources the assistant's answer was based on: [{type, id, title}]. Null for user messages. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(updatable = false)
    private List<Map<String, Object>> sources;

    /** Insertion order, filled by the database. */
    @Column(insertable = false, updatable = false)
    private Long seq;

    public static ChatMessage of(UUID userId, UUID conversationId, Role role, String content,
                                 List<Map<String, Object>> sources) {
        ChatMessage message = new ChatMessage();
        message.userId = userId;
        message.conversationId = conversationId;
        message.role = role;
        message.content = content;
        message.sources = sources;
        return message;
    }
}

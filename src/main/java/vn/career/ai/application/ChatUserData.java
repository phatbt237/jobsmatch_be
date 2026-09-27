package vn.career.ai.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.career.ai.domain.ChatMessage;
import vn.career.ai.infrastructure.ChatMessageRepository;
import vn.career.common.userdata.UserDataEraser;
import vn.career.common.userdata.UserDataExporter;

/** Chatbot history: deleted with the account and included in the personal data export. */
@Component
@RequiredArgsConstructor
class ChatUserData implements UserDataEraser, UserDataExporter {

    private final ChatMessageRepository messages;

    @Override
    @Transactional
    public void eraseUserData(UUID userId) {
        messages.deleteByUserId(userId);
    }

    @Override
    public String section() {
        return "chatMessages";
    }

    @Override
    @Transactional(readOnly = true)
    public Object exportUserData(UUID userId) {
        return messages.findByUserIdOrderBySeqAsc(userId).stream().map(ChatUserData::export).toList();
    }

    private static Map<String, Object> export(ChatMessage message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("conversationId", message.getConversationId());
        map.put("role", message.getRole());
        map.put("content", message.getContent());
        map.put("sources", message.getSources());
        map.put("createdAt", message.getCreatedAt());
        return map;
    }
}

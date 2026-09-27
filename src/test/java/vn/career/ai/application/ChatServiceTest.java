package vn.career.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import vn.career.ai.api.dto.ChatRequestBody;
import vn.career.ai.api.dto.ChatResponse;
import vn.career.ai.domain.ChatMessage;
import vn.career.ai.domain.CrisisDetector;
import vn.career.ai.infrastructure.ChatMessageRepository;
import vn.career.ai.infrastructure.ChatRateLimiter;
import vn.career.ai.infrastructure.VectorStore;
import vn.career.auth.application.AccountAccessApi;
import vn.career.catalog.application.CatalogQueryApi;
import vn.career.common.exception.AppException;
import vn.career.common.exception.ErrorCode;
import vn.career.common.exception.ForbiddenException;
import vn.career.common.exception.NotFoundException;
import vn.career.common.exception.RateLimitException;
import vn.career.common.security.AuthenticatedUser;

class ChatServiceTest {

    private final UUID userId = UUID.randomUUID();
    private final AuthenticatedUser caller = new AuthenticatedUser(userId, "STUDENT");
    private final AccountAccessApi access = mock(AccountAccessApi.class);
    private final CatalogQueryApi catalog = mock(CatalogQueryApi.class);
    private final ChatRateLimiter limiter = mock(ChatRateLimiter.class);
    private final LlmGateway gateway = mock(LlmGateway.class);
    private final VectorStore store = mock(VectorStore.class);
    private final ChatMessageRepository messages = mock(ChatMessageRepository.class);
    private final AiProperties properties = new AiProperties(false, 1536,
            new AiProperties.Explanation(3, Duration.ofMillis(1)), new AiProperties.Chat(20, 10, 2000),
            new AiProperties.Rag(5, 500, 50, 0.0, false));
    private final ChatService service = new ChatService(access, catalog, limiter, gateway, store, messages, properties);

    private final UUID povId = UUID.randomUUID();
    private final UUID majorId = UUID.randomUUID();

    ChatServiceTest() {
        when(access.canUseAiFeatures(userId)).thenReturn(true);
        when(catalog.majorExists(majorId)).thenReturn(true);
        when(gateway.embed(any())).thenReturn(new float[] {1f});
        when(store.search(any(), anyInt(), any(), anyDouble())).thenReturn(List.of(hit("POV", povId, "Bài của mentor")));
        when(gateway.chat(any())).thenReturn(new LlmGateway.ChatAnswer("Trả lời", "m", true));
        when(messages.findByConversationIdAndUserIdOrderBySeqDesc(any(), any(), any(Pageable.class))).thenReturn(List.of());
    }

    private static VectorStore.Hit hit(String type, UUID id, String title) {
        return new VectorStore.Hit(type, id, 0, "nội dung " + title, Map.of("title", title, "jobTitle", "Kỹ sư"), 0.9);
    }

    private ChatResponse ask(String message) {
        return service.chat(caller, new ChatRequestBody(message, null, null));
    }

    private static ChatMessage message(ChatMessage.Role role, String content, long seq) {
        ChatMessage m = ChatMessage.of(UUID.randomUUID(), UUID.randomUUID(), role, content, null);
        ReflectionTestUtils.setField(m, "seq", seq);
        return m;
    }

    // ---------- guards ----------

    @Test
    void consentIsRequired() {
        when(access.canUseAiFeatures(userId)).thenReturn(false);

        assertThatThrownBy(() -> ask("Ngành CNTT?")).isInstanceOf(ForbiddenException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode()).isEqualTo(ErrorCode.CONSENT_REQUIRED));
        verify(gateway, never()).chat(any());
        verify(limiter, never()).countOrReject(any());
    }

    @Test
    void theRateLimitIsCheckedBeforeAnythingExpensiveHappens() {
        doThrow(new RateLimitException("too many", 60)).when(limiter).countOrReject(userId);

        assertThatThrownBy(() -> ask("Ngành CNTT?")).isInstanceOf(RateLimitException.class);
        verify(gateway, never()).embed(any());
        verify(gateway, never()).chat(any());
        verify(messages, never()).saveAll(any());
    }

    @Test
    void anUnknownMajorIsRejected() {
        assertThatThrownBy(() -> service.chat(caller, new ChatRequestBody("Ngành gì?", UUID.randomUUID(), null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void aTooLongMessageIsRejected() {
        assertThatThrownBy(() -> ask("a".repeat(2001))).isInstanceOf(AppException.class);
    }

    @Test
    void someoneElsesConversationLooksLikeItDoesNotExist() {
        UUID conversation = UUID.randomUUID();
        when(messages.existsByConversationIdAndUserId(conversation, userId)).thenReturn(false);

        assertThatThrownBy(() -> service.chat(caller, new ChatRequestBody("Ngành CNTT?", null, conversation)))
                .isInstanceOf(NotFoundException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode()).isEqualTo(ErrorCode.CONVERSATION_NOT_FOUND));
        verify(limiter, never()).countOrReject(any());
    }

    // ---------- safety ----------

    @Test
    void distressGetsTheSupportMessageAndNeverReachesTheModel() {
        ChatResponse response = ask("Em muốn chết");

        assertThat(response.answer()).isEqualTo(CrisisDetector.SUPPORT_MESSAGE);
        assertThat(response.sources()).isEmpty();
        verify(gateway, never()).chat(any());
        verify(gateway, never()).embed(any());
        verify(store, never()).search(any(), anyInt(), any(), anyDouble());
        verify(messages).saveAll(any());   // the exchange is still recorded
    }

    // ---------- retrieval and answering ----------

    @Test
    void theMajorFilterAndTopKAreUsedForRetrieval() {
        service.chat(caller, new ChatRequestBody("Ngành này làm gì?", majorId, null));

        verify(store).search(any(), eq(5), eq(majorId), eq(0.0));
    }

    @Test
    void theAnswerListsTheSourcesItWasBasedOnWithoutDuplicates() {
        UUID other = UUID.randomUUID();
        when(store.search(any(), anyInt(), any(), anyDouble())).thenReturn(List.of(
                hit("POV", povId, "Bài A"), hit("POV", povId, "Bài A"), hit("MAJOR", other, "Ngành B")));

        ChatResponse response = ask("Ngành CNTT?");

        assertThat(response.sources()).extracting(s -> s.type() + ":" + s.title()).containsExactly("POV:Bài A", "MAJOR:Ngành B");
        assertThat(response.sources().get(0).id()).isEqualTo(povId);
        assertThat(response.answer()).isEqualTo("Trả lời");
    }

    @Test
    void noSourcesAreShownWhenTheModelDidNotUseTheContext() {
        when(gateway.chat(any())).thenReturn(new LlmGateway.ChatAnswer("Xin lỗi, ngoài phạm vi", "m", false));

        assertThat(ask("Hôm nay ăn gì?").sources()).isEmpty();
    }

    @Test
    void theModelReceivesTheSystemPromptTheContextAndTheQuestion() {
        ask("Ngành CNTT áp lực không?");

        ArgumentCaptor<LlmGateway.ChatRequest> captor = ArgumentCaptor.forClass(LlmGateway.ChatRequest.class);
        verify(gateway).chat(captor.capture());
        LlmGateway.ChatRequest request = captor.getValue();
        assertThat(request.userMessage()).isEqualTo("Ngành CNTT áp lực không?");
        assertThat(request.systemPrompt()).contains("Chỉ trả lời dựa trên phần \"Ngữ cảnh\"").contains("Không đóng vai một người thật")
                .contains("ngoài phạm vi").contains("khủng hoảng tâm lý");
        assertThat(request.context()).hasSize(1);
        assertThat(request.context().get(0).sourceType()).isEqualTo("POV");
        assertThat(request.context().get(0).title()).isEqualTo("Bài của mentor");
    }

    // ---------- history ----------

    @Test
    void onlyTheLastTenMessagesAreSentAsHistoryOldestFirst() {
        UUID conversation = UUID.randomUUID();
        when(messages.existsByConversationIdAndUserId(conversation, userId)).thenReturn(true);
        List<ChatMessage> newestFirst = new ArrayList<>();
        for (int i = 10; i >= 1; i--) {   // the repository returns them newest first
            newestFirst.add(message(i % 2 == 0 ? ChatMessage.Role.ASSISTANT : ChatMessage.Role.USER, "tin " + i, i));
        }
        when(messages.findByConversationIdAndUserIdOrderBySeqDesc(eq(conversation), eq(userId), any(Pageable.class)))
                .thenReturn(newestFirst);

        service.chat(caller, new ChatRequestBody("Câu tiếp theo về ngành", null, conversation));

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(messages).findByConversationIdAndUserIdOrderBySeqDesc(eq(conversation), eq(userId), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(10);
        ArgumentCaptor<LlmGateway.ChatRequest> captor = ArgumentCaptor.forClass(LlmGateway.ChatRequest.class);
        verify(gateway).chat(captor.capture());
        assertThat(captor.getValue().history()).extracting(LlmGateway.ChatTurn::content)
                .containsExactly("tin 1", "tin 2", "tin 3", "tin 4", "tin 5", "tin 6", "tin 7", "tin 8", "tin 9", "tin 10");
        assertThat(captor.getValue().history().get(0).role()).isEqualTo("USER");
        assertThat(captor.getValue().history().get(1).role()).isEqualTo("ASSISTANT");
    }

    @Test
    void bothMessagesAreSavedInOrderAndConversationsAreReused() {
        UUID conversation = UUID.randomUUID();
        when(messages.existsByConversationIdAndUserId(conversation, userId)).thenReturn(true);

        ChatResponse response = service.chat(caller, new ChatRequestBody("Ngành CNTT?", null, conversation));

        assertThat(response.conversationId()).isEqualTo(conversation);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> saved = ArgumentCaptor.forClass(List.class);
        verify(messages).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(ChatMessage::getRole)
                .containsExactly(ChatMessage.Role.USER, ChatMessage.Role.ASSISTANT);
        assertThat(saved.getValue()).extracting(ChatMessage::getConversationId).containsOnly(conversation);
        assertThat(saved.getValue().get(1).getSources()).hasSize(1);
        assertThat(saved.getValue().get(0).getSources()).isNull();
    }

    @Test
    void aNewConversationGetsAnId() {
        assertThat(ask("Ngành CNTT?").conversationId()).isNotNull();
    }

    // ---------- failures ----------

    @Test
    void aFailingModelBecomesA503AndSavesNothing() {
        when(gateway.chat(any())).thenThrow(new LlmGateway.LlmException("down"));

        assertThatThrownBy(() -> ask("Ngành CNTT?")).isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));
        verify(messages, never()).saveAll(any());
    }

    @Test
    void aFailingEmbeddingBecomesA503() {
        when(gateway.embed(any())).thenThrow(new LlmGateway.LlmException("down"));

        assertThatThrownBy(() -> ask("Ngành CNTT?")).isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));
        verify(gateway, never()).chat(any());
    }
}

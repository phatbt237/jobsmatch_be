package vn.career.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.career.recommendation.application.ExplanationApi;

class ExplanationServiceTest {

    private final UUID attemptId = UUID.randomUUID();
    private final UUID majorId = UUID.randomUUID();
    private final ExplanationApi api = mock(ExplanationApi.class);
    private final LlmGateway gateway = mock(LlmGateway.class);
    private final AiProperties properties = new AiProperties(false, 1536,
            new AiProperties.Explanation(3, Duration.ofMillis(1)), new AiProperties.Chat(20, 10, 2000),
            new AiProperties.Rag(5, 500, 50, 0.0, false));
    private final ExplanationService service = new ExplanationService(api, new ExplanationPromptBuilder(),
            new ExplanationParser(new ObjectMapper()), gateway, properties);

    private final String good = "{\"summary\":\"Tổng quan\",\"majors\":[{\"majorId\":\"" + majorId + "\",\"text\":\"Vì...\"}]}";

    ExplanationServiceTest() {
        when(api.loadInput(attemptId)).thenReturn(new ExplanationApi.ExplanationInput(attemptId, false,
                List.of(new ExplanationApi.DimensionLine("I", "Nghiên cứu", "INTEREST", 80)),
                List.of(new ExplanationApi.MajorLine(majorId, "Khoa học dữ liệu", 1, 0.8, Map.of()))));
    }

    @Test
    void successOnTheFirstTryStoresTheExplanation() {
        when(gateway.generateExplanation(any())).thenReturn(new LlmGateway.LlmResponse(good, "m1"));

        service.explain(attemptId);

        verify(api).saveExplanation(eq(attemptId), eq("Tổng quan"), eq(Map.of(majorId, "Vì...")), eq("m1"));
        verify(gateway, times(1)).generateExplanation(any());
        verify(api, never()).markFailed(any());
    }

    @Test
    void theModelReceivesThePromptAndTheStructuredInput() {
        when(gateway.generateExplanation(any())).thenReturn(new LlmGateway.LlmResponse(good, "m1"));

        service.explain(attemptId);

        var captor = org.mockito.ArgumentCaptor.forClass(LlmGateway.ExplanationRequest.class);
        verify(gateway).generateExplanation(captor.capture());
        assertThat(captor.getValue().prompt()).contains("majorId=" + majorId);
        assertThat(captor.getValue().input().attemptId()).isEqualTo(attemptId);
    }

    @Test
    void twoFailuresAreRetriedAndTheThirdTrySucceeds() {
        when(gateway.generateExplanation(any()))
                .thenThrow(new LlmGateway.LlmException("boom"))
                .thenThrow(new LlmGateway.LlmException("boom"))
                .thenReturn(new LlmGateway.LlmResponse(good, "m1"));

        service.explain(attemptId);

        verify(gateway, times(3)).generateExplanation(any());
        verify(api).saveExplanation(eq(attemptId), any(), any(), any());
        verify(api, never()).markFailed(any());
    }

    @Test
    void whenEveryTryFailsTheExplanationIsMarkedFailed() {
        when(gateway.generateExplanation(any())).thenThrow(new LlmGateway.LlmException("down"));

        service.explain(attemptId);

        verify(gateway, times(3)).generateExplanation(any());
        verify(api).markFailed(attemptId);
        verify(api, never()).saveExplanation(any(), any(), any(), any());
    }

    @Test
    void aReplyThatIsNotValidJsonCountsAsAFailedTry() {
        when(gateway.generateExplanation(any()))
                .thenReturn(new LlmGateway.LlmResponse("Xin chào, đây không phải JSON", "m1"))
                .thenReturn(new LlmGateway.LlmResponse(good, "m1"));

        service.explain(attemptId);

        verify(gateway, times(2)).generateExplanation(any());
        verify(api).saveExplanation(eq(attemptId), any(), any(), any());
    }

    @Test
    void aReplyMissingAMajorCountsAsAFailedTryAndCanEndAsFailed() {
        when(gateway.generateExplanation(any()))
                .thenReturn(new LlmGateway.LlmResponse("{\"summary\":\"S\",\"majors\":[]}", "m1"));

        service.explain(attemptId);

        verify(gateway, times(3)).generateExplanation(any());
        verify(api).markFailed(attemptId);
    }

    @Test
    void nothingIsAskedWhenThereAreNoRecommendations() {
        UUID empty = UUID.randomUUID();
        when(api.loadInput(empty)).thenReturn(new ExplanationApi.ExplanationInput(empty, false, List.of(), List.of()));

        service.explain(empty);

        verify(gateway, never()).generateExplanation(any());
        verify(api, never()).markFailed(any());
    }

    @Test
    void maxAttemptsOfOneMeansNoRetry() {
        AiProperties once = new AiProperties(false, 1536, new AiProperties.Explanation(1, Duration.ofMillis(1)),
                properties.chat(), properties.rag());
        ExplanationService single = new ExplanationService(api, new ExplanationPromptBuilder(),
                new ExplanationParser(new ObjectMapper()), gateway, once);
        when(gateway.generateExplanation(any())).thenThrow(new LlmGateway.LlmException("down"));

        single.explain(attemptId);

        verify(gateway, times(1)).generateExplanation(any());
        verify(api).markFailed(attemptId);
    }
}

package vn.career.recommendation.application;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vn.career.recommendation.api.dto.ResultResponse;

/** Keeps the open SSE connections of clients waiting for an explanation and pushes the final result to them. */
@Component
@Slf4j
public class ResultStreamRegistry {

    static final long TIMEOUT_MS = 5 * 60 * 1000L;
    static final String EVENT_NAME = "explanation";

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(UUID attemptId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        List<SseEmitter> list = emitters.computeIfAbsent(attemptId, id -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        Runnable cleanup = () -> remove(attemptId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(() -> {
            cleanup.run();
            emitter.complete();
        });
        emitter.onError(e -> cleanup.run());
        return emitter;
    }

    /** Sends the result as an "explanation" event to everybody waiting on the attempt and closes their streams. */
    public void publish(UUID attemptId, ResultResponse result) {
        List<SseEmitter> waiting = emitters.remove(attemptId);
        if (waiting == null) {
            return;
        }
        for (SseEmitter emitter : waiting) {
            send(emitter, result);
        }
    }

    private static void send(SseEmitter emitter, ResultResponse result) {
        try {
            emitter.send(SseEmitter.event().name(EVENT_NAME).data(result, MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (IOException | IllegalStateException e) {
            log.debug("SSE client is gone ({})", e.getClass().getSimpleName());
            emitter.completeWithError(e);
        }
    }

    private void remove(UUID attemptId, SseEmitter emitter) {
        emitters.computeIfPresent(attemptId, (id, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
    }

    int waitingCount(UUID attemptId) {
        return emitters.getOrDefault(attemptId, List.of()).size();
    }
}

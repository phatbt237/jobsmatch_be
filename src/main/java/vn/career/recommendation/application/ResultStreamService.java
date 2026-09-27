package vn.career.recommendation.application;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vn.career.common.security.AuthenticatedUser;
import vn.career.recommendation.api.dto.ResultResponse;

/** Server-sent events for a result: the client waits on one connection until the explanation is ready. */
@Service
@RequiredArgsConstructor
public class ResultStreamService {

    private final ResultService resultService;
    private final ResultStreamRegistry registry;

    /**
     * Opens a stream after checking access. If the explanation is already final the event is sent at once,
     * otherwise the connection stays open until {@link ExplanationApi} finishes it (or the 5 minute timeout).
     */
    public SseEmitter open(AuthenticatedUser caller, UUID attemptId) {
        ResultResponse result = resultService.getResult(caller, attemptId);
        SseEmitter emitter = registry.register(attemptId);
        // Re-read after registering so a completion that happened in between cannot be missed.
        ResultResponse latest = ResultService.isPending(result) ? resultService.buildResult(attemptId) : result;
        if (!ResultService.isPending(latest)) {
            registry.publish(attemptId, latest);
        }
        return emitter;
    }
}

package com.dbtraining.reconx.service;

import com.dbtraining.reconx.dto.TradeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ============================================================================
 * TICKET-ADV104 (backend counterpart) — GET /v1/trades/stream
 *
 * WHAT:    In-memory registry of open SseEmitters, broadcasting every
 *          TradeResponse produced by TradeService.create/update/updateStatus
 *          to every connected browser.
 * WHY:     static-dashboard/js/sse.js (Day 7) and useTradeStream.js (Day 8-9)
 *          both construct a plain `new EventSource('/api/v1/trades/stream')`
 *          with no auth wiring — this endpoint was referenced by three days
 *          of guides as already "scaffolded" but was never actually
 *          implemented anywhere in the course material. This is that
 *          implementation, added to unblock the ★ headline live-feed feature.
 * ============================================================================
 */
@Service
public class TradeStreamService {

    private static final Logger log = LoggerFactory.getLogger(TradeStreamService.class);
    private static final long EMITTER_TIMEOUT_MS = 0L; // never times out server-side

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(ex -> emitters.remove(emitter));
        try {
            // Tomcat/Spring don't commit the response (and the browser's
            // EventSource doesn't fire onopen) until the first byte is
            // written — send an immediate comment so "connected" reflects
            // reality instead of waiting for the first real trade event.
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException ex) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    public void broadcast(TradeResponse trade) {
        for (SseEmitter emitter : emitters) {
            try {
                // No .name(...) — useTradeStream.js / sse.js both listen via
                // the default onmessage handler, which only fires for the
                // unnamed "message" event type, not a custom named one.
                emitter.send(SseEmitter.event().data(trade));
            } catch (IOException | IllegalStateException ex) {
                log.debug("Removing dead SSE emitter: {}", ex.getMessage());
                emitters.remove(emitter);
            }
        }
    }
}

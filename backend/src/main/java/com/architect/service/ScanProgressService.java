package com.architect.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages SSE emitters for real-time scan progress streaming.
 * Multiple browser tabs can subscribe to the same repo's scan stream.
 */
@Slf4j
@Service
public class ScanProgressService {

    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long repoId) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5-min max
        emitters.computeIfAbsent(repoId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(repoId, emitter));
        emitter.onTimeout(()    -> remove(repoId, emitter));
        emitter.onError(e       -> remove(repoId, emitter));

        return emitter;
    }

    /** Emit a named event to all subscribers of this repo's scan. */
    public void emit(Long repoId, String eventName, Object data) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(repoId);
        if (list == null || list.isEmpty()) return;

        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (Exception e) {
                dead.add(emitter);
            }
        }
        list.removeAll(dead);
    }

    /** Close all subscribers when scan finishes or fails. */
    public void complete(Long repoId) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.remove(repoId);
        if (list != null) {
            list.forEach(e -> {
                try { e.complete(); } catch (Exception ignored) {}
            });
        }
    }

    private void remove(Long repoId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(repoId);
        if (list != null) list.remove(emitter);
    }
}

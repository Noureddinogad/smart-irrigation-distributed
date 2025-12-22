package com.irrigation.dashboardapi.web;

import core.dto.AlertDTO;
import core.rmi.RmiClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.*;

@RestController
public class AlertStreamController {

    private final RmiClient rmi;

    // device -> emitters
    private final ConcurrentHashMap<String, CopyOnWriteArraySet<SseEmitter>> emittersByDevice = new ConcurrentHashMap<>();

    // device -> last sent alert id (prevents duplicates + prevents replay on connect)
    private final ConcurrentHashMap<String, Long> lastSentIdByDevice = new ConcurrentHashMap<>();

    // single scheduler for polling RMI
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public AlertStreamController(RmiClient rmi) {
        this.rmi = rmi;
        scheduler.scheduleAtFixedRate(this::pollAlerts, 0, 2, TimeUnit.SECONDS);
    }

    @GetMapping(value = "/api/devices/{device}/alerts/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @PathVariable String device,
            @RequestParam(required = false) Long sinceId
    ) {
        SseEmitter emitter = new SseEmitter(0L); // no timeout

        emittersByDevice
                .computeIfAbsent(device, d -> new CopyOnWriteArraySet<>())
                .add(emitter);

        emitter.onCompletion(() -> remove(device, emitter));
        emitter.onTimeout(() -> remove(device, emitter));
        emitter.onError(e -> remove(device, emitter));

        // ✅ Set initial last-sent id (so first connect does NOT replay whole history)
        try {
            long startId;

            if (sinceId != null && sinceId > 0) {
                startId = sinceId;
            } else {
                // IMPORTANT: your getAlerts() appears to return ASC (oldest->newest),
                // so limit=1 would give oldest. Instead, get a small batch and take MAX(id).
                List<AlertDTO> batch = rmi.call(svc -> svc.getAlerts(device, "1970-01-01T00:00:00Z", 50));
                startId = maxId(batch);
            }

            lastSentIdByDevice.put(device, startId);
        } catch (Exception e) {
            lastSentIdByDevice.put(device, 0L);
        }

        // handshake
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (Exception ignored) {}

        return emitter;
    }

    private void pollAlerts() {
        try {
            for (String device : emittersByDevice.keySet()) {

                long lastSent = lastSentIdByDevice.getOrDefault(device, 0L);

                // Pull recent alerts and push only new ones by id
                List<AlertDTO> batch = rmi.call(svc -> svc.getAlerts(device, "1970-01-01T00:00:00Z", 50));
                if (batch == null || batch.isEmpty()) continue;

                // Ensure we emit in increasing id order
                batch.sort(Comparator.comparingLong(a -> a != null && a.id != null ? a.id : Long.MIN_VALUE));

                for (AlertDTO a : batch) {
                    if (a == null || a.id == null) continue;
                    if (a.id <= lastSent) continue;

                    broadcast(device, a);
                    lastSent = a.id;
                }

                lastSentIdByDevice.put(device, lastSent);
            }
        } catch (Exception ignored) {
            // don't crash SSE
        }
    }

    private void broadcast(String device, AlertDTO alert) {
        Set<SseEmitter> set = emittersByDevice.get(device);
        if (set == null || set.isEmpty()) return;

        for (SseEmitter emitter : set) {
            try {
                emitter.send(SseEmitter.event().name("alert").data(alert));
            } catch (Exception e) {
                remove(device, emitter);
            }
        }
    }

    private void remove(String device, SseEmitter emitter) {
        Set<SseEmitter> set = emittersByDevice.get(device);
        if (set != null) {
            set.remove(emitter);
            if (set.isEmpty()) {
                emittersByDevice.remove(device);
                // optional cleanup:
                // lastSentIdByDevice.remove(device);
            }
        }
    }

    private long maxId(List<AlertDTO> alerts) {
        long max = 0L;
        if (alerts == null) return 0L;
        for (AlertDTO a : alerts) {
            if (a == null || a.id == null) continue;
            if (a.id > max) max = a.id;
        }
        return max;
    }
}

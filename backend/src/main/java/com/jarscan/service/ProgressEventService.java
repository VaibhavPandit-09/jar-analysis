package com.jarscan.service;

import com.jarscan.dto.ProgressEvent;
import com.jarscan.job.AnalysisJob;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class ProgressEventService {

    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(AnalysisJob job) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(job.id(), ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(job.id(), emitter));
        emitter.onTimeout(() -> remove(job.id(), emitter));
        emitter.onError(ignored -> remove(job.id(), emitter));
        job.events().forEach(event -> send(emitter, event));
        return emitter;
    }

    public void publish(AnalysisJob job, ProgressEvent event) {
        job.events().add(event);
        emitters.getOrDefault(job.id(), List.of()).forEach(emitter -> send(emitter, event));
    }

    private void send(SseEmitter emitter, ProgressEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event.type().name())
                    .data(event));
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
    }

    private void remove(String jobId, SseEmitter emitter) {
        emitters.getOrDefault(jobId, List.of()).remove(emitter);
    }
}

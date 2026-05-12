package com.jarscan.service;

import com.jarscan.dto.ProgressEvent;
import com.jarscan.job.AnalysisJob;
import com.jarscan.model.InputType;
import com.jarscan.model.ProgressEventType;
import com.jarscan.model.ProgressPhase;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.file.Files;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressEventServiceTests {

    @Test
    void storesPublishedEventsForReplay() throws Exception {
        ProgressEventService progressEventService = new ProgressEventService();
        AnalysisJob job = new AnalysisJob("job-1", InputType.ARCHIVE_UPLOAD, Files.createTempDirectory("job-1"));

        ProgressEvent event = new ProgressEvent(
                "job-1",
                ProgressEventType.PROGRESS,
                ProgressPhase.ANALYZING,
                "Analyzing sample.jar",
                40,
                "sample.jar",
                1,
                2,
                Instant.now()
        );
        progressEventService.publish(job, event);

        SseEmitter emitter = progressEventService.subscribe(job);

        assertThat(job.events()).containsExactly(event);
        assertThat(emitter).isNotNull();
    }
}

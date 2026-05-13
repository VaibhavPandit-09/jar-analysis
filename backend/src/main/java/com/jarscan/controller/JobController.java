package com.jarscan.controller;

import com.jarscan.dto.AnalysisJobResponse;
import com.jarscan.dto.AnalysisJobStatusResponse;
import com.jarscan.dto.AnalysisResult;
import com.jarscan.service.AnalysisJobService;
import com.jarscan.service.ReportExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final AnalysisJobService analysisJobService;
    private final ReportExportService reportExportService;

    public JobController(AnalysisJobService analysisJobService, ReportExportService reportExportService) {
        this.analysisJobService = analysisJobService;
        this.reportExportService = reportExportService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public AnalysisJobResponse createJob(@RequestParam("files") List<MultipartFile> files) {
        return analysisJobService.createJob(files);
    }

    @GetMapping(path = "/{jobId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents(@PathVariable String jobId) {
        return analysisJobService.subscribe(jobId);
    }

    @GetMapping(path = "/{jobId}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public AnalysisJobStatusResponse getStatus(@PathVariable String jobId) {
        return analysisJobService.getStatus(jobId);
    }

    @GetMapping(path = "/{jobId}/result", produces = MediaType.APPLICATION_JSON_VALUE)
    public AnalysisResult getResult(@PathVariable String jobId) {
        return analysisJobService.getResult(jobId);
    }

    @GetMapping(path = "/{jobId}/export")
    public ResponseEntity<byte[]> exportReport(@PathVariable String jobId, @RequestParam(defaultValue = "json") String format) {
        AnalysisResult result = analysisJobService.getResult(jobId);
        byte[] content = reportExportService.export(result, format);
        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"jarscan-%s.%s\"".formatted(jobId, extension(format)))
                .contentType(reportExportService.mediaType(format))
                .body(content);
    }

    @PostMapping(path = "/{jobId}/cancel", produces = MediaType.APPLICATION_JSON_VALUE)
    public AnalysisJobStatusResponse cancelJob(@PathVariable String jobId) {
        return analysisJobService.cancel(jobId);
    }

    private String extension(String format) {
        return switch (format.toLowerCase()) {
            case "markdown" -> "md";
            case "cyclonedx-json" -> "cdx.json";
            default -> format.toLowerCase();
        };
    }
}

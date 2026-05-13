package com.jarscan.controller;

import com.jarscan.dto.StoredScanResponse;
import com.jarscan.dto.StoredScanSummaryResponse;
import com.jarscan.dto.UpdateStoredScanRequest;
import com.jarscan.dto.ScanComparisonResponse;
import com.jarscan.model.InputType;
import com.jarscan.model.JobStatus;
import com.jarscan.model.Severity;
import com.jarscan.persistence.ScanSearchCriteria;
import com.jarscan.service.ReportExportService;
import com.jarscan.service.ScanHistoryService;
import com.jarscan.service.ScanComparisonService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(path = "/api/scans", produces = MediaType.APPLICATION_JSON_VALUE)
public class ScanHistoryController {

    private final ScanHistoryService scanHistoryService;
    private final ScanComparisonService scanComparisonService;
    private final ReportExportService reportExportService;

    public ScanHistoryController(
            ScanHistoryService scanHistoryService,
            ScanComparisonService scanComparisonService,
            ReportExportService reportExportService
    ) {
        this.scanHistoryService = scanHistoryService;
        this.scanComparisonService = scanComparisonService;
        this.reportExportService = reportExportService;
    }

    @GetMapping
    public List<StoredScanSummaryResponse> listScans(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) InputType inputType,
            @RequestParam(required = false) JobStatus status,
            @RequestParam(required = false) Severity severity,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset
    ) {
        return scanHistoryService.listScans(new ScanSearchCriteria(q, inputType, status, severity, sort, direction, limit, offset));
    }

    @GetMapping("/{scanId}")
    public StoredScanResponse getScan(@PathVariable String scanId) {
        return scanHistoryService.getStoredScan(scanId);
    }

    @GetMapping("/by-job/{jobId}")
    public StoredScanResponse getScanByJobId(@PathVariable String jobId) {
        return scanHistoryService.findStoredScanByJobId(jobId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown job: " + jobId));
    }

    @GetMapping("/{scanId}/export")
    public ResponseEntity<byte[]> exportScan(@PathVariable String scanId, @RequestParam(defaultValue = "json") String format) {
        StoredScanResponse stored = scanHistoryService.getStoredScan(scanId);
        if (stored.result() == null) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Stored scan has no result payload");
        }
        byte[] content = reportExportService.export(stored.result(), format);
        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"jarscan-%s.%s\"".formatted(scanId, extension(format)))
                .contentType(reportExportService.mediaType(format))
                .body(content);
    }

    @GetMapping("/compare")
    public ScanComparisonResponse compareScans(
            @RequestParam("base") String baselineScanId,
            @RequestParam("target") String targetScanId
    ) {
        return scanComparisonService.compareScans(baselineScanId, targetScanId);
    }

    @DeleteMapping("/{scanId}")
    public void deleteScan(@PathVariable String scanId) {
        scanHistoryService.deleteScan(scanId);
    }

    @PatchMapping(path = "/{scanId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public StoredScanSummaryResponse updateScan(
            @PathVariable String scanId,
            @RequestBody UpdateStoredScanRequest request
    ) {
        return scanHistoryService.updateMetadata(scanId, request);
    }

    private String extension(String format) {
        return switch (format.toLowerCase()) {
            case "markdown" -> "md";
            case "cyclonedx-json" -> "cdx.json";
            default -> format.toLowerCase();
        };
    }
}

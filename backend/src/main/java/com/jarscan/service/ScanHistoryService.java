package com.jarscan.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarscan.config.JarScanProperties;
import com.jarscan.dto.AnalysisJobStatusResponse;
import com.jarscan.dto.AnalysisResult;
import com.jarscan.dto.StoredScanResponse;
import com.jarscan.dto.StoredScanSummaryResponse;
import com.jarscan.dto.UpdateStoredScanRequest;
import com.jarscan.job.AnalysisJob;
import com.jarscan.model.InputType;
import com.jarscan.model.JobStatus;
import com.jarscan.model.Severity;
import com.jarscan.persistence.PersistedScanRecord;
import com.jarscan.persistence.PersistedScanUpsert;
import com.jarscan.persistence.ScanHistoryRepository;
import com.jarscan.persistence.ScanSearchCriteria;
import com.jarscan.persistence.StoredScanMetadataUpdate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ScanHistoryService {

    private final ScanHistoryRepository repository;
    private final ObjectMapper objectMapper;
    private final JarScanProperties properties;

    public ScanHistoryService(ScanHistoryRepository repository, ObjectMapper objectMapper, JarScanProperties properties) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void persistCompletedScan(AnalysisJob job) {
        AnalysisResult result = job.result();
        if (result == null) {
            throw new IllegalStateException("Cannot persist completed scan without a result");
        }
        Optional<PersistedScanRecord> existing = repository.findByJobId(job.id());
        repository.upsert(new PersistedScanUpsert(
                existing.map(PersistedScanRecord::id).orElse(UUID.randomUUID().toString()),
                job.id(),
                job.inputType(),
                job.inputName(),
                job.inputHash(),
                job.status(),
                job.startedAt(),
                job.completedAt(),
                durationMs(job.startedAt(), job.completedAt()),
                result.summary().totalArtifacts(),
                result.summary().totalDependencies(),
                result.summary().totalVulnerabilities(),
                result.summary().critical(),
                result.summary().high(),
                result.summary().medium(),
                result.summary().low(),
                result.summary().info(),
                result.summary().unknown(),
                result.summary().highestCvss(),
                result.summary().requiredJavaVersion(),
                properties.appVersion(),
                existing.map(PersistedScanRecord::notes).orElse(null),
                existing.map(PersistedScanRecord::tags).orElse(List.of()),
                serializeResult(result),
                result,
                existing.map(PersistedScanRecord::createdAt).orElse(Instant.now()),
                Instant.now()
        ));
    }

    public void persistTerminalMetadata(AnalysisJob job) {
        Optional<PersistedScanRecord> existing = repository.findByJobId(job.id());
        repository.upsert(new PersistedScanUpsert(
                existing.map(PersistedScanRecord::id).orElse(UUID.randomUUID().toString()),
                job.id(),
                job.inputType(),
                job.inputName(),
                job.inputHash(),
                job.status(),
                job.startedAt(),
                job.completedAt(),
                durationMs(job.startedAt(), job.completedAt()),
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                null,
                null,
                properties.appVersion(),
                existing.map(PersistedScanRecord::notes).orElse(joinMessages(job.errors())),
                existing.map(PersistedScanRecord::tags).orElse(List.of()),
                null,
                null,
                existing.map(PersistedScanRecord::createdAt).orElse(Instant.now()),
                Instant.now()
        ));
    }

    public List<StoredScanSummaryResponse> listScans(ScanSearchCriteria criteria) {
        return repository.findAll(criteria).stream().map(this::toSummaryResponse).toList();
    }

    public StoredScanResponse getStoredScan(String scanId) {
        PersistedScanRecord record = repository.findById(scanId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown scan: " + scanId));
        return new StoredScanResponse(toSummaryResponse(record), record.result());
    }

    public Optional<AnalysisResult> findResultByJobId(String jobId) {
        return repository.findByJobId(jobId).map(PersistedScanRecord::result);
    }

    public Optional<AnalysisJobStatusResponse> findStatusByJobId(String jobId) {
        return repository.findByJobId(jobId)
                .map(record -> new AnalysisJobStatusResponse(
                        record.jobId(),
                        record.status(),
                        record.inputType(),
                        record.startedAt(),
                        record.completedAt(),
                        record.status() == JobStatus.COMPLETED ? "Completed (persisted)" : record.status().name(),
                        List.of(),
                        List.of()
                ));
    }

    public void deleteScan(String scanId) {
        if (!repository.deleteById(scanId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown scan: " + scanId);
        }
    }

    public StoredScanSummaryResponse updateMetadata(String scanId, UpdateStoredScanRequest request) {
        return repository.updateMetadata(scanId, new StoredScanMetadataUpdate(request.notes(), normalizeTags(request.tags())))
                .map(this::toSummaryResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown scan: " + scanId));
    }

    public StoredScanSummaryResponse toSummaryResponse(PersistedScanRecord record) {
        return new StoredScanSummaryResponse(
                record.id(),
                record.jobId(),
                record.inputType(),
                record.inputName(),
                record.inputHash(),
                record.status(),
                record.startedAt(),
                record.completedAt(),
                record.durationMs(),
                record.totalArtifacts(),
                record.totalDependencies(),
                record.totalVulnerabilities(),
                record.criticalCount(),
                record.highCount(),
                record.mediumCount(),
                record.lowCount(),
                record.infoCount(),
                record.unknownCount(),
                record.highestCvss(),
                record.requiredJavaVersion(),
                highestSeverity(record),
                record.createdAppVersion(),
                record.notes(),
                record.tags(),
                record.createdAt(),
                record.updatedAt()
        );
    }

    private Severity highestSeverity(PersistedScanRecord record) {
        if (record.criticalCount() > 0) return Severity.CRITICAL;
        if (record.highCount() > 0) return Severity.HIGH;
        if (record.mediumCount() > 0) return Severity.MEDIUM;
        if (record.lowCount() > 0) return Severity.LOW;
        if (record.infoCount() > 0) return Severity.INFO;
        return Severity.UNKNOWN;
    }

    private Long durationMs(Instant startedAt, Instant completedAt) {
        if (startedAt == null || completedAt == null) {
            return null;
        }
        return Duration.between(startedAt, completedAt).toMillis();
    }

    private String serializeResult(AnalysisResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize analysis result", ex);
        }
    }

    private String joinMessages(List<String> messages) {
        return messages == null || messages.isEmpty() ? null : String.join("\n", messages);
    }

    private List<String> normalizeTags(List<String> tags) {
        return tags == null ? List.of() : tags.stream().filter(tag -> tag != null && !tag.isBlank()).map(String::trim).distinct().toList();
    }
}

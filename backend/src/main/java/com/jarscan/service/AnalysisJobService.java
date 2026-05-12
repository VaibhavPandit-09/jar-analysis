package com.jarscan.service;

import com.jarscan.dto.AnalysisJobResponse;
import com.jarscan.dto.AnalysisJobStatusResponse;
import com.jarscan.dto.AnalysisResult;
import com.jarscan.dto.AnalysisSummary;
import com.jarscan.dto.ArtifactAnalysis;
import com.jarscan.dto.ProgressEvent;
import com.jarscan.job.AnalysisJob;
import com.jarscan.job.JobCancelledException;
import com.jarscan.model.InputType;
import com.jarscan.model.JobStatus;
import com.jarscan.model.ProgressEventType;
import com.jarscan.model.ProgressPhase;
import com.jarscan.util.FilenameSanitizer;
import com.jarscan.util.JobDirectories;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

@Service
public class AnalysisJobService {

    private final Map<String, AnalysisJob> jobs = new ConcurrentHashMap<>();
    private final ExecutorService analysisExecutor;
    private final ProgressEventService progressEventService;
    private final JarAnalyzerService jarAnalyzerService;

    public AnalysisJobService(
            ExecutorService analysisExecutor,
            ProgressEventService progressEventService,
            JarAnalyzerService jarAnalyzerService
    ) {
        this.analysisExecutor = analysisExecutor;
        this.progressEventService = progressEventService;
        this.jarAnalyzerService = jarAnalyzerService;
    }

    public AnalysisJobResponse createJob(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one file is required");
        }

        String jobId = UUID.randomUUID().toString();
        InputType inputType = detectInputType(files);
        Path workspaceDir = JobDirectories.createWorkspace(jobId);
        AnalysisJob job = new AnalysisJob(jobId, inputType, workspaceDir);
        jobs.put(jobId, job);

        job.future(analysisExecutor.submit(() -> runJob(job, files)));
        return new AnalysisJobResponse(jobId);
    }

    public SseEmitter subscribe(String jobId) {
        return progressEventService.subscribe(getJob(jobId));
    }

    public AnalysisJobStatusResponse getStatus(String jobId) {
        AnalysisJob job = getJob(jobId);
        return new AnalysisJobStatusResponse(
                job.id(),
                job.status(),
                job.inputType(),
                job.startedAt(),
                job.completedAt(),
                job.message(),
                List.copyOf(job.warnings()),
                List.copyOf(job.errors())
        );
    }

    public AnalysisResult getResult(String jobId) {
        AnalysisJob job = getJob(jobId);
        if (job.result() == null) {
            throw new ResponseStatusException(HttpStatus.ACCEPTED, "Result is not available yet");
        }
        return job.result();
    }

    public AnalysisJobStatusResponse cancel(String jobId) {
        AnalysisJob job = getJob(jobId);
        job.cancel();
        job.status(JobStatus.CANCELLED);
        job.completedAt(Instant.now());
        job.message("Cancelled");
        publish(job, ProgressEventType.CANCELLED, ProgressPhase.CANCELLED, "Analysis cancelled", 100, null, null, null);
        return getStatus(jobId);
    }

    private void runJob(AnalysisJob job, List<MultipartFile> files) {
        try {
            job.status(JobStatus.RUNNING);
            job.startedAt(Instant.now());
            publish(job, ProgressEventType.STARTED, ProgressPhase.PREPARING, "Preparing analysis workspace", 0, null, 0, files.size());
            checkCancelled(job);

            List<Path> storedFiles = storeFiles(job, files);
            publish(job, ProgressEventType.PROGRESS, ProgressPhase.VALIDATING_UPLOAD, "Validated upload", 15, null, 0, storedFiles.size());
            checkCancelled(job);

            List<ArtifactAnalysis> artifacts = new ArrayList<>();
            int total = storedFiles.size();
            for (int index = 0; index < storedFiles.size(); index++) {
                Path path = storedFiles.get(index);
                publish(job, ProgressEventType.PROGRESS, ProgressPhase.ANALYZING,
                        "Analyzing " + path.getFileName(),
                        Math.min(95, 20 + ((index * 70) / Math.max(1, total))),
                        path.getFileName().toString(),
                        index,
                        total);
                checkCancelled(job);
                artifacts.add(jarAnalyzerService.analyze(path));
            }

            AnalysisResult result = new AnalysisResult(
                    job.id(),
                    JobStatus.COMPLETED,
                    job.inputType(),
                    job.startedAt(),
                    Instant.now(),
                    new AnalysisSummary(
                            artifacts.size(),
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
                            "Unknown"
                    ),
                    artifacts,
                    null,
                    List.copyOf(job.warnings()),
                    List.copyOf(job.errors())
            );
            job.result(result);
            job.status(JobStatus.COMPLETED);
            job.completedAt(result.completedAt());
            job.message("Completed");
            publish(job, ProgressEventType.COMPLETED, ProgressPhase.COMPLETED, "Analysis completed", 100, null, artifacts.size(), artifacts.size());
        } catch (JobCancelledException ex) {
            job.status(JobStatus.CANCELLED);
            job.completedAt(Instant.now());
            job.message(ex.getMessage());
        } catch (Exception ex) {
            job.status(JobStatus.FAILED);
            job.completedAt(Instant.now());
            job.message("Analysis failed");
            job.errors().add(ex.getMessage());
            publish(job, ProgressEventType.ERROR, ProgressPhase.FAILED, ex.getMessage(), null, null, null, null);
        } finally {
            JobDirectories.deleteQuietly(job.workspaceDir());
        }
    }

    private List<Path> storeFiles(AnalysisJob job, List<MultipartFile> files) throws IOException {
        Path uploadsDir = Files.createDirectories(job.workspaceDir().resolve("uploads"));
        List<Path> storedFiles = new ArrayList<>();
        for (MultipartFile file : files) {
            String original = file.getOriginalFilename();
            String safeName = FilenameSanitizer.sanitize(original == null ? "upload.bin" : original);
            Path target = uploadsDir.resolve(safeName).normalize();
            file.transferTo(target);
            storedFiles.add(target);
        }
        return storedFiles;
    }

    private InputType detectInputType(List<MultipartFile> files) {
        boolean pomPresent = files.stream()
                .map(MultipartFile::getOriginalFilename)
                .filter(name -> name != null)
                .anyMatch(name -> name.equalsIgnoreCase("pom.xml"));
        return pomPresent ? InputType.POM : InputType.ARCHIVE_UPLOAD;
    }

    private AnalysisJob getJob(String jobId) {
        AnalysisJob job = jobs.get(jobId);
        if (job == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown job: " + jobId);
        }
        return job;
    }

    private void checkCancelled(AnalysisJob job) {
        if (job.isCancelled() || Thread.currentThread().isInterrupted()) {
            throw new JobCancelledException("Analysis cancelled");
        }
    }

    private void publish(
            AnalysisJob job,
            ProgressEventType type,
            ProgressPhase phase,
            String message,
            Integer percent,
            String currentItem,
            Integer completedItems,
            Integer totalItems
    ) {
        job.message(message);
        progressEventService.publish(job, new ProgressEvent(
                job.id(),
                type,
                phase,
                message,
                percent,
                currentItem,
                completedItems,
                totalItems,
                Instant.now()
        ));
    }
}

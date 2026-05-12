package com.jarscan.job;

import com.jarscan.dto.AnalysisResult;
import com.jarscan.dto.ProgressEvent;
import com.jarscan.model.InputType;
import com.jarscan.model.JobStatus;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class AnalysisJob {

    private final String id;
    private final InputType inputType;
    private final Path workspaceDir;
    private final List<ProgressEvent> events = new CopyOnWriteArrayList<>();
    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private final List<String> errors = new CopyOnWriteArrayList<>();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicReference<Process> activeProcess = new AtomicReference<>();
    private volatile JobStatus status = JobStatus.QUEUED;
    private volatile Instant startedAt;
    private volatile Instant completedAt;
    private volatile String message = "Queued";
    private volatile AnalysisResult result;
    private volatile Future<?> future;

    public AnalysisJob(String id, InputType inputType, Path workspaceDir) {
        this.id = id;
        this.inputType = inputType;
        this.workspaceDir = workspaceDir;
    }

    public String id() {
        return id;
    }

    public InputType inputType() {
        return inputType;
    }

    public Path workspaceDir() {
        return workspaceDir;
    }

    public List<ProgressEvent> events() {
        return events;
    }

    public List<String> warnings() {
        return warnings;
    }

    public List<String> errors() {
        return errors;
    }

    public JobStatus status() {
        return status;
    }

    public void status(JobStatus status) {
        this.status = status;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public void startedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public void completedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String message() {
        return message;
    }

    public void message(String message) {
        this.message = message;
    }

    public AnalysisResult result() {
        return result;
    }

    public void result(AnalysisResult result) {
        this.result = result;
    }

    public Future<?> future() {
        return future;
    }

    public void future(Future<?> future) {
        this.future = future;
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void cancel() {
        cancelled.set(true);
        Process process = activeProcess.get();
        if (process != null) {
            process.destroyForcibly();
        }
        Future<?> currentFuture = future;
        if (currentFuture != null) {
            currentFuture.cancel(true);
        }
    }

    public void activeProcess(Process process) {
        activeProcess.set(process);
    }
}

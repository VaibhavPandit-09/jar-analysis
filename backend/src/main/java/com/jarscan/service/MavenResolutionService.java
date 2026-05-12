package com.jarscan.service;

import com.jarscan.config.JarScanProperties;
import com.jarscan.job.AnalysisJob;
import com.jarscan.maven.MavenResolutionResult;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Service
public class MavenResolutionService {

    private final JarScanProperties properties;

    public MavenResolutionService(JarScanProperties properties) {
        this.properties = properties;
    }

    public MavenResolutionResult resolveDependencies(AnalysisJob job, Path pomPath, String scope, Consumer<String> logConsumer)
            throws IOException, InterruptedException {
        Path workingDirectory = pomPath.getParent();
        Path dependencyDir = java.nio.file.Files.createDirectories(workingDirectory.resolve("resolved-deps"));

        runCommand(job, workingDirectory, logConsumer, List.of(
                "mvn",
                "-f", pomPath.toString(),
                "dependency:copy-dependencies",
                "-DoutputDirectory=" + dependencyDir,
                "-DincludeScope=" + scope,
                "-Dmdep.useRepositoryLayout=false",
                "-DskipTests"
        ));

        String dependencyTree = runCommand(job, workingDirectory, logConsumer, List.of(
                "mvn",
                "-f", pomPath.toString(),
                "dependency:tree",
                "-DoutputType=text"
        ));

        List<Path> artifacts = new ArrayList<>();
        try (var paths = java.nio.file.Files.list(dependencyDir)) {
            paths.filter(path -> {
                        String lower = path.getFileName().toString().toLowerCase();
                        return lower.endsWith(".jar") || lower.endsWith(".war") || lower.endsWith(".ear");
                    })
                    .sorted()
                    .forEach(artifacts::add);
        }

        return new MavenResolutionResult(artifacts, dependencyTree);
    }

    private String runCommand(
            AnalysisJob job,
            Path workingDirectory,
            Consumer<String> logConsumer,
            List<String> command
    ) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workingDirectory.toFile());
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        job.activeProcess(process);

        StringBuilder output = new StringBuilder();
        try {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(System.lineSeparator());
                    logConsumer.accept(line);
                    if (job.isCancelled()) {
                        process.destroyForcibly();
                        throw new InterruptedException("Maven resolution cancelled");
                    }
                }
            }

            boolean finished = process.waitFor(properties.mavenTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("Maven command timed out after " + Duration.ofSeconds(properties.mavenTimeoutSeconds()));
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("Maven command failed: " + String.join(" ", command));
            }
            return output.toString();
        } finally {
            job.activeProcess(null);
        }
    }
}

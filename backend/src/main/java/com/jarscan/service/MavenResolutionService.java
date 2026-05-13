package com.jarscan.service;

import com.jarscan.config.JarScanProperties;
import com.jarscan.dto.DependencyTree;
import com.jarscan.job.AnalysisJob;
import com.jarscan.maven.MavenDependencyTreeParser;
import com.jarscan.maven.MavenResolutionResult;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Service
public class MavenResolutionService {

    private final JarScanProperties properties;
    private final MavenDependencyTreeParser dependencyTreeParser;

    public MavenResolutionService(JarScanProperties properties, MavenDependencyTreeParser dependencyTreeParser) {
        this.properties = properties;
        this.dependencyTreeParser = dependencyTreeParser;
    }

    public MavenResolutionResult resolveDependencies(AnalysisJob job, Path pomPath, String scope, Consumer<String> logConsumer)
            throws IOException, InterruptedException {
        Path workingDirectory = pomPath.getParent();
        Path dependencyDir = Files.createDirectories(workingDirectory.resolve("resolved-deps"));

        runCommand(job, workingDirectory, logConsumer, buildCopyDependenciesCommand(pomPath, dependencyDir, scope));

        DependencyTreeCapture dependencyTreeCapture = captureDependencyTree(job, pomPath, workingDirectory, logConsumer);

        List<Path> artifacts = new ArrayList<>();
        try (var paths = Files.list(dependencyDir)) {
            paths.filter(path -> {
                        String lower = path.getFileName().toString().toLowerCase();
                        return lower.endsWith(".jar") || lower.endsWith(".war") || lower.endsWith(".ear");
                    })
                    .sorted()
                    .forEach(artifacts::add);
        }

        return new MavenResolutionResult(artifacts, dependencyTreeCapture.dependencyTree(), dependencyTreeCapture.dependencyTreeText());
    }

    List<String> buildCopyDependenciesCommand(Path pomPath, Path dependencyDir, String scope) {
        return List.of(
                "mvn",
                "-f", pomPath.toString(),
                "dependency:copy-dependencies",
                "-DoutputDirectory=" + dependencyDir,
                "-DincludeScope=" + scope,
                "-Dmdep.useRepositoryLayout=false",
                "-DskipTests"
        );
    }

    List<String> buildDependencyTreeJsonCommand(Path pomPath, Path outputFile) {
        return List.of(
                "mvn",
                "-f", pomPath.toString(),
                "dependency:tree",
                "-DoutputType=json",
                "-DoutputFile=" + outputFile
        );
    }

    List<String> buildDependencyTreeTextCommand(Path pomPath, Path outputFile) {
        return List.of(
                "mvn",
                "-f", pomPath.toString(),
                "dependency:tree",
                "-DoutputType=text",
                "-DoutputFile=" + outputFile
        );
    }

    private DependencyTreeCapture captureDependencyTree(
            AnalysisJob job,
            Path pomPath,
            Path workingDirectory,
            Consumer<String> logConsumer
    ) throws IOException, InterruptedException {
        Path jsonOutputFile = workingDirectory.resolve("dependency-tree.json");
        CommandResult jsonAttempt = runCommandAllowFailure(
                job,
                workingDirectory,
                logConsumer,
                buildDependencyTreeJsonCommand(pomPath, jsonOutputFile)
        );
        String jsonOutput = readCommandOutput(jsonOutputFile, jsonAttempt.output());
        if (jsonAttempt.exitCode() == 0 && !jsonOutput.isBlank()) {
            try {
                return new DependencyTreeCapture(dependencyTreeParser.parseJson(jsonOutput), null);
            } catch (IOException ex) {
                logConsumer.accept("Dependency tree JSON parsing failed. Falling back to Maven text tree output.");
            }
        } else {
            logConsumer.accept("Dependency tree JSON output is unavailable. Falling back to Maven text tree output.");
        }

        Path textOutputFile = workingDirectory.resolve("dependency-tree.txt");
        CommandResult textCommand = runCommand(
                job,
                workingDirectory,
                logConsumer,
                buildDependencyTreeTextCommand(pomPath, textOutputFile)
        );
        String textOutput = readCommandOutput(textOutputFile, textCommand.output());
        return new DependencyTreeCapture(dependencyTreeParser.parseText(textOutput), textOutput);
    }

    private String readCommandOutput(Path outputFile, String processOutput) throws IOException {
        if (Files.exists(outputFile)) {
            return Files.readString(outputFile);
        }
        return processOutput == null ? "" : processOutput;
    }

    private CommandResult runCommand(
            AnalysisJob job,
            Path workingDirectory,
            Consumer<String> logConsumer,
            List<String> command
    ) throws IOException, InterruptedException {
        CommandResult result = executeCommand(job, workingDirectory, logConsumer, command);
        if (result.exitCode() != 0) {
            throw new IllegalStateException("Maven command failed: " + String.join(" ", command));
        }
        return result;
    }

    private CommandResult runCommandAllowFailure(
            AnalysisJob job,
            Path workingDirectory,
            Consumer<String> logConsumer,
            List<String> command
    ) throws IOException, InterruptedException {
        return executeCommand(job, workingDirectory, logConsumer, command);
    }

    private CommandResult executeCommand(
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
            return new CommandResult(process.exitValue(), output.toString());
        } finally {
            job.activeProcess(null);
        }
    }

    private record CommandResult(int exitCode, String output) {
    }

    private record DependencyTreeCapture(DependencyTree dependencyTree, String dependencyTreeText) {
    }
}

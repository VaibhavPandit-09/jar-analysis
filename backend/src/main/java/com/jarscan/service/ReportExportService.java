package com.jarscan.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarscan.dto.AnalysisResult;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class ReportExportService {

    private final ObjectMapper objectMapper;
    private final SbomService sbomService;

    public ReportExportService(ObjectMapper objectMapper, SbomService sbomService) {
        this.objectMapper = objectMapper;
        this.sbomService = sbomService;
    }

    public byte[] export(AnalysisResult result, String format) {
        return switch (format.toLowerCase()) {
            case "json" -> exportJson(result);
            case "md", "markdown" -> exportMarkdown(result).getBytes(StandardCharsets.UTF_8);
            case "html" -> exportHtml(result).getBytes(StandardCharsets.UTF_8);
            case "cyclonedx-json" -> sbomService.exportCycloneDx(result);
            default -> throw new IllegalArgumentException("Unsupported export format: " + format);
        };
    }

    public MediaType mediaType(String format) {
        return switch (format.toLowerCase()) {
            case "json" -> MediaType.APPLICATION_JSON;
            case "md", "markdown" -> new MediaType("text", "markdown", StandardCharsets.UTF_8);
            case "html" -> MediaType.TEXT_HTML;
            case "cyclonedx-json" -> new MediaType("application", "vnd.cyclonedx+json", StandardCharsets.UTF_8);
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    private byte[] exportJson(AnalysisResult result) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(result);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize JSON report", ex);
        }
    }

    private String exportMarkdown(AnalysisResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append("# JARScan Report\n\n");
        builder.append("- Job ID: ").append(result.jobId()).append('\n');
        builder.append("- Status: ").append(result.status()).append('\n');
        builder.append("- Artifacts: ").append(result.summary().totalArtifacts()).append('\n');
        builder.append("- Vulnerabilities: ").append(result.summary().totalVulnerabilities()).append('\n');
        builder.append("- Highest CVSS: ").append(result.summary().highestCvss()).append("\n\n");
        builder.append("## Artifacts\n\n");
        result.artifacts().forEach(artifact -> builder
                .append("- **").append(artifact.fileName()).append("**")
                .append(" (`").append(artifact.javaVersion().requiredJava()).append("`)")
                .append(" - ").append(artifact.vulnerabilityCount()).append(" vulnerability findings\n"));
        return builder.toString();
    }

    private String exportHtml(AnalysisResult result) {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8" />
                  <title>JARScan Report</title>
                  <style>
                    body { font-family: Arial, sans-serif; margin: 2rem; background: #f7fafc; color: #111827; }
                    .card { background: white; border-radius: 16px; padding: 1.5rem; margin-bottom: 1rem; box-shadow: 0 10px 30px rgba(15, 23, 42, 0.08); }
                    h1, h2 { margin-top: 0; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <h1>JARScan Report</h1>
                    <p>Job ID: %s</p>
                    <p>Status: %s</p>
                    <p>Artifacts analyzed: %d</p>
                    <p>Total vulnerabilities: %d</p>
                  </div>
                  <div class="card">
                    <h2>Artifacts</h2>
                    <ul>%s</ul>
                  </div>
                </body>
                </html>
                """.formatted(
                result.jobId(),
                result.status(),
                result.summary().totalArtifacts(),
                result.summary().totalVulnerabilities(),
                result.artifacts().stream()
                        .map(artifact -> "<li>%s (%s) - %d findings</li>".formatted(
                                artifact.fileName(),
                                artifact.javaVersion().requiredJava(),
                                artifact.vulnerabilityCount()))
                        .reduce("", String::concat)
        );
    }
}

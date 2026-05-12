package com.jarscan.service;

import com.jarscan.config.JarScanProperties;
import com.jarscan.util.FilenameSanitizer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class ProjectArchiveService {

    private final JarScanProperties properties;

    public ProjectArchiveService(JarScanProperties properties) {
        this.properties = properties;
    }

    public Path extractProjectArchive(Path zipPath, Path workspaceDir) throws IOException {
        String baseName = zipPath.getFileName().toString().replaceFirst("(?i)\\.zip$", "");
        Path extractRoot = Files.createDirectories(workspaceDir.resolve("project").resolve(FilenameSanitizer.sanitize(baseName)));
        long extractedBytes = 0L;
        int fileCount = 0;

        try (InputStream inputStream = Files.newInputStream(zipPath);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String entryName = entry.getName().replace('\\', '/');
                if (entryName.isBlank()) {
                    continue;
                }
                if (entryName.startsWith("/") || entryName.contains("../") || entryName.contains("..\\")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsafe ZIP entry: " + entry.getName());
                }

                Path target = extractRoot.resolve(entryName).normalize();
                if (!target.startsWith(extractRoot)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsafe ZIP entry: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }

                fileCount++;
                if (fileCount > properties.projectZipMaxFiles()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project ZIP exceeds max file count of " + properties.projectZipMaxFiles());
                }

                Files.createDirectories(target.getParent());
                long written = copyCapped(zipInputStream, target, properties.projectZipMaxExtractedSizeBytes() - extractedBytes);
                extractedBytes += written;
                if (extractedBytes > properties.projectZipMaxExtractedSizeBytes()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project ZIP exceeds max extracted size of " + properties.projectZipMaxExtractedSizeBytes() + " bytes");
                }
            }
        }

        return extractRoot;
    }

    private long copyCapped(InputStream inputStream, Path target, long remainingBudget) throws IOException {
        if (remainingBudget <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project ZIP exceeds configured extraction budget");
        }
        byte[] buffer = new byte[8192];
        long total = 0L;
        try (var outputStream = Files.newOutputStream(target)) {
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                total += read;
                if (total > remainingBudget) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project ZIP exceeds configured extraction budget");
                }
                outputStream.write(buffer, 0, read);
            }
        }
        return total;
    }
}

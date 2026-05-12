package com.jarscan.util;

import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JobDirectories {

    private JobDirectories() {
    }

    public static Path createWorkspace(String jobId) {
        try {
            return Files.createTempDirectory("jarscan-" + jobId + "-");
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create workspace for job " + jobId, ex);
        }
    }

    public static void deleteQuietly(Path path) {
        try {
            FileSystemUtils.deleteRecursively(path);
        } catch (IOException ignored) {
            // Best effort cleanup after job completion.
        }
    }
}

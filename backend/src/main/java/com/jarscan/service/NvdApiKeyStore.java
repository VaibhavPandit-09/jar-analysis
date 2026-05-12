package com.jarscan.service;

import com.jarscan.config.JarScanProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

@Service
public class NvdApiKeyStore {

    private final JarScanProperties properties;

    public NvdApiKeyStore(JarScanProperties properties) {
        this.properties = properties;
    }

    public void save(String apiKey) {
        Path path = apiKeyPath();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, apiKey.strip(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            restrictPermissions(path);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to save NVD API key", ex);
        }
    }

    public Optional<String> read() {
        Path path = apiKeyPath();
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            String value = Files.readString(path, StandardCharsets.UTF_8).trim();
            return value.isBlank() ? Optional.empty() : Optional.of(value);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read NVD API key", ex);
        }
    }

    public boolean delete() {
        try {
            return Files.deleteIfExists(apiKeyPath());
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to remove NVD API key", ex);
        }
    }

    public Optional<Instant> lastModifiedAt() {
        Path path = apiKeyPath();
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.getLastModifiedTime(path).toInstant());
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    public Path apiKeyPath() {
        return Path.of(properties.dataDir()).resolve("secrets").resolve("nvd-api-key");
    }

    public String storageMode() {
        return "FILE:" + apiKeyPath().toAbsolutePath().normalize();
    }

    private void restrictPermissions(Path path) {
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            try {
                Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rw-------");
                Files.setPosixFilePermissions(path, permissions);
            } catch (IOException ignored) {
                // Best effort only on local filesystems that support POSIX permissions.
            }
        }
    }
}

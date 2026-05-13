package com.jarscan.service;

import com.jarscan.config.JarScanProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectArchiveServiceTests {

    private final ProjectArchiveService projectArchiveService = new ProjectArchiveService(new JarScanProperties(
            "0.1.0-test",
            Files.createTempDirectory("jarscan-tests").toString(),
            Files.createTempDirectory("jarscan-tests-db").resolve("jarscan.db").toString(),
            "dependency-check.sh",
            4,
            10_000_000,
            250,
            3,
            1_024,
            60,
            "runtime"
    ));

    ProjectArchiveServiceTests() throws IOException {
    }

    @Test
    void extractsProjectZipSafelyIntoWorkspace() throws IOException {
        Path zip = Files.createTempFile("project-", ".zip");
        writeZip(zip,
                new EntrySpec("demo/pom.xml", "<project/>".getBytes()),
                new EntrySpec("demo/src/main/resources/application.yml", "spring:".getBytes()));

        Path extracted = projectArchiveService.extractProjectArchive(zip, Files.createTempDirectory("workspace"));

        assertThat(Files.exists(extracted.resolve("demo/pom.xml"))).isTrue();
        assertThat(Files.exists(extracted.resolve("demo/src/main/resources/application.yml"))).isTrue();
    }

    @Test
    void rejectsZipSlipEntries() throws IOException {
        Path zip = Files.createTempFile("zip-slip-", ".zip");
        writeZip(zip, new EntrySpec("../evil.txt", "nope".getBytes()));

        assertThatThrownBy(() -> projectArchiveService.extractProjectArchive(zip, Files.createTempDirectory("workspace")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unsafe ZIP entry");
    }

    @Test
    void rejectsArchiveWhenFileCountLimitIsExceeded() throws IOException {
        Path zip = Files.createTempFile("too-many-", ".zip");
        writeZip(zip,
                new EntrySpec("a.txt", "a".getBytes()),
                new EntrySpec("b.txt", "b".getBytes()),
                new EntrySpec("c.txt", "c".getBytes()),
                new EntrySpec("d.txt", "d".getBytes()));

        assertThatThrownBy(() -> projectArchiveService.extractProjectArchive(zip, Files.createTempDirectory("workspace")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("max file count");
    }

    @Test
    void rejectsArchiveWhenExtractionBudgetIsExceeded() throws IOException {
        Path zip = Files.createTempFile("too-large-", ".zip");
        writeZip(zip, new EntrySpec("payload.bin", new byte[2_000]));

        assertThatThrownBy(() -> projectArchiveService.extractProjectArchive(zip, Files.createTempDirectory("workspace")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("extraction budget");
    }

    private void writeZip(Path zip, EntrySpec... entries) throws IOException {
        try (ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (EntrySpec entry : entries) {
                outputStream.putNextEntry(new ZipEntry(entry.path()));
                outputStream.write(entry.bytes());
                outputStream.closeEntry();
            }
        }
    }

    private record EntrySpec(String path, byte[] bytes) {
    }
}

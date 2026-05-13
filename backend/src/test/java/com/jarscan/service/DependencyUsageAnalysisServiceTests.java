package com.jarscan.service;

import com.jarscan.config.JarScanProperties;
import com.jarscan.dto.ArtifactAnalysis;
import com.jarscan.dto.DependencyTree;
import com.jarscan.dto.DependencyTreeNode;
import com.jarscan.dto.DependencyUsageFinding;
import com.jarscan.maven.MavenDependencyAnalyzeEntry;
import com.jarscan.maven.MavenDependencyAnalyzeResult;
import com.jarscan.model.InputType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyUsageAnalysisServiceTests {

    @TempDir
    Path tempDir;

    private final DependencyUsageAnalysisService service = new DependencyUsageAnalysisService(
            new BytecodeReferenceExtractorService(),
            new DependencyPathService()
    );

    private final JarAnalyzerService jarAnalyzerService = new JarAnalyzerService(new JarScanProperties(
            "0.1.0-test",
            "/tmp/jarscan-tests",
            "/tmp/jarscan-tests/jarscan.db",
            "dependency-check.sh",
            4,
            10_000_000,
            250,
            500,
            20_000_000,
            60,
            "runtime"
    ));

    @Test
    void mapsBytecodeReferencesToResolvedDependencyArtifacts() throws Exception {
        Path dependencyJar = createDependencyJar(
                "org.example",
                "feature-lib",
                "1.0.0",
                Map.of("com/example/dep/Feature.java", "package com.example.dep; public class Feature { }")
        );
        ArtifactAnalysis artifact = jarAnalyzerService.analyze(dependencyJar, tempDir, new java.util.ArrayList<>());
        Path appClasses = compileAppClasses(
                "com/example/app/Main.java",
                "package com.example.app; import com.example.dep.Feature; public class Main { Feature feature = new Feature(); }",
                dependencyJar
        );

        DependencyUsageAnalysisService.DependencyUsageAnalysisResult result = service.analyze(
                InputType.POM,
                List.of(artifact),
                List.of(dependencyJar),
                tree("org.example", "feature-lib", "1.0.0"),
                new MavenDependencyAnalyzeResult(
                        List.of(new MavenDependencyAnalyzeEntry("org.example", "feature-lib", "jar", null, "1.0.0", "compile")),
                        List.of(),
                        List.of(),
                        ""
                ),
                null,
                null,
                List.of(appClasses),
                List.of(),
                new java.util.ArrayList<>()
        );

        assertThat(result.dependencyUsageFindings()).singleElement().satisfies(finding -> {
            assertThat(finding.status()).isEqualTo("USED");
            assertThat(finding.confidence()).isEqualTo("HIGH");
            assertThat(finding.evidence()).anyMatch(item -> item.contains("Compiled bytecode references"));
        });
    }

    @Test
    void marksHighConfidenceDeclaredButUnusedWhenEvidenceIsAbsent() throws Exception {
        Path dependencyJar = createDependencyJar(
                "org.example",
                "unused-lib",
                "1.0.0",
                Map.of("com/example/dep/Unused.java", "package com.example.dep; public class Unused { }")
        );
        ArtifactAnalysis artifact = jarAnalyzerService.analyze(dependencyJar, tempDir, new java.util.ArrayList<>());
        Path appClasses = compileAppClasses(
                "com/example/app/Main.java",
                "package com.example.app; public class Main { String name = \"demo\"; }",
                null
        );

        DependencyUsageFinding finding = service.analyze(
                InputType.POM,
                List.of(artifact),
                List.of(dependencyJar),
                tree("org.example", "unused-lib", "1.0.0"),
                new MavenDependencyAnalyzeResult(
                        List.of(),
                        List.of(),
                        List.of(new MavenDependencyAnalyzeEntry("org.example", "unused-lib", "jar", null, "1.0.0", "runtime")),
                        ""
                ),
                null,
                null,
                List.of(appClasses),
                List.of(),
                new java.util.ArrayList<>()
        ).dependencyUsageFindings().getFirst();

        assertThat(finding.status()).isEqualTo("DECLARED_BUT_UNUSED");
        assertThat(finding.confidence()).isEqualTo("HIGH");
    }

    @Test
    void lowersConfidenceWhenServiceLoaderOrSpringMetadataSuggestRuntimeUsage() throws Exception {
        Path dependencyJar = createDependencyJar(
                "org.example",
                "runtime-spi",
                "1.0.0",
                Map.of("com/example/spi/Provider.java", "package com.example.spi; public class Provider { }"),
                Map.of(
                        "META-INF/services/com.example.Service", "com.example.spi.Provider\n",
                        "META-INF/spring.factories", "org.springframework.boot.autoconfigure.EnableAutoConfiguration=com.example.spi.Provider\n"
                )
        );
        ArtifactAnalysis artifact = jarAnalyzerService.analyze(dependencyJar, tempDir, new java.util.ArrayList<>());
        Path appClasses = compileAppClasses(
                "com/example/app/Main.java",
                "package com.example.app; public class Main { int value = 1; }",
                null
        );

        DependencyUsageFinding finding = service.analyze(
                InputType.POM,
                List.of(artifact),
                List.of(dependencyJar),
                tree("org.example", "runtime-spi", "1.0.0"),
                null,
                null,
                null,
                List.of(appClasses),
                List.of(),
                new java.util.ArrayList<>()
        ).dependencyUsageFindings().getFirst();

        assertThat(finding.status()).isEqualTo("POSSIBLY_RUNTIME_USED");
        assertThat(finding.confidence()).isEqualTo("LOW");
        assertThat(finding.evidence()).anyMatch(item -> item.contains("ServiceLoader provider metadata"));
        assertThat(finding.evidence()).anyMatch(item -> item.contains("Spring auto-configuration metadata"));
    }

    @Test
    void detectsAwsS3UsageFromBytecodeReferences() throws Exception {
        Path bundleJar = createDependencyJar(
                "software.amazon.awssdk",
                "bundle",
                "2.25.0",
                Map.of("software/amazon/awssdk/services/s3/S3Client.java", "package software.amazon.awssdk.services.s3; public class S3Client { }")
        );
        ArtifactAnalysis artifact = jarAnalyzerService.analyze(bundleJar, tempDir, new java.util.ArrayList<>());
        Path appClasses = compileAppClasses(
                "com/example/app/Main.java",
                "package com.example.app; import software.amazon.awssdk.services.s3.S3Client; public class Main { S3Client client = new S3Client(); }",
                bundleJar
        );

        DependencyUsageAnalysisService.DependencyUsageAnalysisResult result = service.analyze(
                InputType.POM,
                List.of(artifact),
                List.of(bundleJar),
                tree("software.amazon.awssdk", "bundle", "2.25.0"),
                null,
                null,
                null,
                List.of(appClasses),
                List.of(),
                new java.util.ArrayList<>()
        );

        assertThat(result.awsServiceModules()).containsExactly("s3");
    }

    private DependencyTree tree(String groupId, String artifactId, String version) {
        return new DependencyTree(
                "TEXT",
                List.of(new DependencyTreeNode(
                        "root",
                        "com.example",
                        "demo",
                        "pom",
                        null,
                        "1.0.0",
                        null,
                        0,
                        null,
                        List.of(new DependencyTreeNode(
                                "child",
                                groupId,
                                artifactId,
                                "jar",
                                null,
                                version,
                                "runtime",
                                1,
                                "root",
                                List.of(),
                                true,
                                false,
                                false,
                                null,
                                false,
                                null,
                                List.of("com.example:demo:1.0.0", groupId + ":" + artifactId + ":" + version)
                        )),
                        false,
                        false,
                        false,
                        null,
                        false,
                        null,
                        List.of("com.example:demo:1.0.0")
                ))
        );
    }

    private Path compileAppClasses(String relativePath, String source, Path classpathJar) throws Exception {
        Path sourceRoot = tempDir.resolve("app-src-" + Math.abs(relativePath.hashCode()));
        Path outputRoot = tempDir.resolve("app-classes-" + Math.abs(relativePath.hashCode()));
        Files.createDirectories(sourceRoot.resolve(relativePath).getParent());
        Files.writeString(sourceRoot.resolve(relativePath), source);
        Files.createDirectories(outputRoot);
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).isNotNull();
        int result;
        if (classpathJar == null) {
            result = compiler.run(null, null, null, "-d", outputRoot.toString(), sourceRoot.resolve(relativePath).toString());
        } else {
            result = compiler.run(null, null, null, "-cp", classpathJar.toString(), "-d", outputRoot.toString(), sourceRoot.resolve(relativePath).toString());
        }
        assertThat(result).isZero();
        return outputRoot;
    }

    private Path createDependencyJar(String groupId, String artifactId, String version, Map<String, String> sources) throws Exception {
        return createDependencyJar(groupId, artifactId, version, sources, Map.of());
    }

    private Path createDependencyJar(String groupId, String artifactId, String version, Map<String, String> sources, Map<String, String> extraEntries) throws Exception {
        Path sourceRoot = tempDir.resolve(artifactId + "-src");
        Path outputRoot = tempDir.resolve(artifactId + "-classes");
        Files.createDirectories(sourceRoot);
        Files.createDirectories(outputRoot);
        for (Map.Entry<String, String> entry : sources.entrySet()) {
            Files.createDirectories(sourceRoot.resolve(entry.getKey()).getParent());
            Files.writeString(sourceRoot.resolve(entry.getKey()), entry.getValue());
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).isNotNull();
        List<String> args = new java.util.ArrayList<>();
        args.add("-d");
        args.add(outputRoot.toString());
        sources.keySet().forEach(path -> args.add(sourceRoot.resolve(path).toString()));
        assertThat(compiler.run(null, null, null, args.toArray(String[]::new))).isZero();

        Path jarPath = tempDir.resolve(artifactId + ".jar");
        try (OutputStream outputStream = Files.newOutputStream(jarPath); JarOutputStream jarOutputStream = new JarOutputStream(outputStream)) {
            Files.walk(outputRoot)
                    .filter(Files::isRegularFile)
                    .forEach(path -> addEntry(outputRoot, path, jarOutputStream));
            addStringEntry(jarOutputStream, "META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties", "groupId=" + groupId + "\nartifactId=" + artifactId + "\nversion=" + version + "\n");
            for (Map.Entry<String, String> extraEntry : extraEntries.entrySet()) {
                addStringEntry(jarOutputStream, extraEntry.getKey(), extraEntry.getValue());
            }
        }
        return jarPath;
    }

    private void addEntry(Path root, Path file, JarOutputStream jarOutputStream) {
        String entryName = root.relativize(file).toString().replace('\\', '/');
        try {
            jarOutputStream.putNextEntry(new JarEntry(entryName));
            jarOutputStream.write(Files.readAllBytes(file));
            jarOutputStream.closeEntry();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void addStringEntry(JarOutputStream jarOutputStream, String entryName, String value) throws IOException {
        jarOutputStream.putNextEntry(new JarEntry(entryName));
        jarOutputStream.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        jarOutputStream.closeEntry();
    }
}

package com.jarscan.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BytecodeReferenceExtractorServiceTests {

    private final BytecodeReferenceExtractorService extractor = new BytecodeReferenceExtractorService();

    @TempDir
    Path tempDir;

    @Test
    void extractsConstantPoolClassReferences() throws Exception {
        Path dependencyClasses = compileJava(
                tempDir.resolve("dep-src"),
                tempDir.resolve("dep-classes"),
                "com/example/dep/Feature.java",
                "package com.example.dep; public class Feature { }",
                null
        );
        Path appClasses = compileJava(
                tempDir.resolve("app-src"),
                tempDir.resolve("app-classes"),
                "com/example/app/Main.java",
                "package com.example.app; import com.example.dep.Feature; public class Main { Feature feature = new Feature(); }",
                dependencyClasses.toString()
        );

        assertThat(extractor.extractReferencedClasses(appClasses))
                .contains("com.example.dep.Feature")
                .contains("com.example.app.Main");
    }

    private Path compileJava(Path sourceRoot, Path outputRoot, String relativePath, String source, String classpath) throws Exception {
        Files.createDirectories(sourceRoot.resolve(relativePath).getParent());
        Files.writeString(sourceRoot.resolve(relativePath), source);
        Files.createDirectories(outputRoot);
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).isNotNull();
        int result;
        if (classpath == null) {
            result = compiler.run(null, null, null, "-d", outputRoot.toString(), sourceRoot.resolve(relativePath).toString());
        } else {
            result = compiler.run(null, null, null, "-cp", classpath, "-d", outputRoot.toString(), sourceRoot.resolve(relativePath).toString());
        }
        assertThat(result).isZero();
        return outputRoot;
    }
}

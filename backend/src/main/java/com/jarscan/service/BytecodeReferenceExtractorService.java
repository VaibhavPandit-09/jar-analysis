package com.jarscan.service;

import org.springframework.stereotype.Service;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

@Service
public class BytecodeReferenceExtractorService {

    public Set<String> extractReferencedClasses(Path classesDirectory) throws IOException {
        if (classesDirectory == null || !Files.exists(classesDirectory)) {
            return Set.of();
        }
        Set<String> references = new LinkedHashSet<>();
        try (var paths = Files.walk(classesDirectory)) {
            for (Path classFile : paths.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".class")).toList()) {
                try (InputStream inputStream = Files.newInputStream(classFile)) {
                    references.addAll(extractReferencedClasses(inputStream));
                } catch (IOException ignored) {
                    // Best-effort usage analysis should continue even if an individual class file is malformed.
                }
            }
        }
        return references;
    }

    public Set<String> extractReferencedClassesFromArchive(Path archivePath, Predicate<String> includeEntry) throws IOException {
        if (archivePath == null || !Files.exists(archivePath)) {
            return Set.of();
        }
        Set<String> references = new LinkedHashSet<>();
        try (JarFile jarFile = new JarFile(archivePath.toFile())) {
            var entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class") || !includeEntry.test(entry.getName())) {
                    continue;
                }
                try (InputStream inputStream = jarFile.getInputStream(entry)) {
                    references.addAll(extractReferencedClasses(inputStream));
                } catch (IOException ignored) {
                    // Best-effort usage analysis should continue even if an individual class file is malformed.
                }
            }
        }
        return references;
    }

    public Set<String> extractProvidedClasses(Path archivePath) throws IOException {
        if (archivePath == null || !Files.exists(archivePath)) {
            return Set.of();
        }
        Set<String> classes = new LinkedHashSet<>();
        try (JarFile jarFile = new JarFile(archivePath.toFile())) {
            var entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class") || entry.getName().endsWith("module-info.class")) {
                    continue;
                }
                classes.add(toDottedClassName(entry.getName()));
            }
        }
        return classes;
    }

    Set<String> extractReferencedClasses(InputStream inputStream) throws IOException {
        try (DataInputStream dataInputStream = new DataInputStream(inputStream)) {
            if (dataInputStream.readInt() != 0xCAFEBABE) {
                return Set.of();
            }
            dataInputStream.readUnsignedShort();
            dataInputStream.readUnsignedShort();
            int constantPoolCount = dataInputStream.readUnsignedShort();
            String[] utf8 = new String[constantPoolCount];
            int[] classNameIndexes = new int[constantPoolCount];
            List<Integer> classEntries = new ArrayList<>();
            for (int index = 1; index < constantPoolCount; index++) {
                int tag = dataInputStream.readUnsignedByte();
                switch (tag) {
                    case 1 -> utf8[index] = dataInputStream.readUTF();
                    case 3, 4 -> dataInputStream.skipBytes(4);
                    case 5, 6 -> {
                        dataInputStream.skipBytes(8);
                        index++;
                    }
                    case 7 -> {
                        classNameIndexes[index] = dataInputStream.readUnsignedShort();
                        classEntries.add(index);
                    }
                    case 8, 16, 19, 20 -> dataInputStream.skipBytes(2);
                    case 9, 10, 11, 12, 17, 18 -> dataInputStream.skipBytes(4);
                    case 15 -> dataInputStream.skipBytes(3);
                    default -> throw new IOException("Unsupported constant pool tag: " + tag);
                }
            }

            Set<String> references = new LinkedHashSet<>();
            for (Integer entryIndex : classEntries) {
                String internalName = utf8[classNameIndexes[entryIndex]];
                if (internalName == null || internalName.isBlank() || internalName.startsWith("[")) {
                    continue;
                }
                references.add(toDottedClassName(internalName));
            }
            return references;
        }
    }

    private String toDottedClassName(String internalName) {
        String candidate = internalName;
        if (candidate.endsWith(".class")) {
            candidate = candidate.substring(0, candidate.length() - ".class".length());
        }
        if (candidate.startsWith("L") && candidate.endsWith(";")) {
            candidate = candidate.substring(1, candidate.length() - 1);
        }
        return candidate.replace('/', '.');
    }
}

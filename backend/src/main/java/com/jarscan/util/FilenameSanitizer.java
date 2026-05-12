package com.jarscan.util;

public final class FilenameSanitizer {

    private FilenameSanitizer() {
    }

    public static String sanitize(String value) {
        String normalized = value.replace('\\', '_').replace('/', '_');
        return normalized.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}

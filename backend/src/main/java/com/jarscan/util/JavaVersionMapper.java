package com.jarscan.util;

public final class JavaVersionMapper {

    private JavaVersionMapper() {
    }

    public static String describe(Integer majorVersion) {
        if (majorVersion == null) {
            return "Unknown";
        }
        return switch (majorVersion) {
            case 45 -> "Java 1.1";
            case 46 -> "Java 1.2";
            case 47 -> "Java 1.3";
            case 48 -> "Java 1.4";
            default -> majorVersion >= 49 ? "Java " + (majorVersion - 44) : "Unknown";
        };
    }
}

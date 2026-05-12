package com.jarscan.dto;

public record JavaVersionInfo(
        Integer minMajor,
        Integer maxMajor,
        String requiredJava
) {
}

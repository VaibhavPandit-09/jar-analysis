package com.jarscan.dto;

import java.util.List;

public record DuplicateClassFinding(
        String findingType,
        String symbol,
        String packageName,
        List<String> artifacts,
        String severity,
        String recommendation,
        String shadowingWarning
) {
}

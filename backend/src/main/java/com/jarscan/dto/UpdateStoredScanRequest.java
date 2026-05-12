package com.jarscan.dto;

import java.util.List;

public record UpdateStoredScanRequest(
        String notes,
        List<String> tags
) {
}

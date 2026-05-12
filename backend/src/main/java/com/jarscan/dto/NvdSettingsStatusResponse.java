package com.jarscan.dto;

import java.time.Instant;

public record NvdSettingsStatusResponse(
        boolean configured,
        String maskedKey,
        String storageMode,
        Instant updatedAt
) {
}

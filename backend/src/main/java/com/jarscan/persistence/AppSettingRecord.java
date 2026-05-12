package com.jarscan.persistence;

import java.time.Instant;

public record AppSettingRecord(
        String key,
        String value,
        boolean encrypted,
        Instant createdAt,
        Instant updatedAt
) {
}

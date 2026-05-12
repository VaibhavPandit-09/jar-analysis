package com.jarscan.persistence;

import java.util.List;

public record StoredScanMetadataUpdate(
        String notes,
        List<String> tags
) {
}

package com.jarscan.dto;

import java.util.List;

public record DependencyComparisonSection(
        int addedCount,
        int removedCount,
        int updatedCount,
        int unchangedCount,
        List<DependencyChangeItem> changes
) {
}

package com.jarscan.persistence;

import com.jarscan.model.InputType;
import com.jarscan.model.JobStatus;
import com.jarscan.model.Severity;

public record ScanSearchCriteria(
        String query,
        InputType inputType,
        JobStatus status,
        Severity severity,
        String sort,
        String direction,
        Integer limit,
        Integer offset
) {
}

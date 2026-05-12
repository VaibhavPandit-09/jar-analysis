package com.jarscan.model;

public enum ProgressPhase {
    PREPARING,
    VALIDATING_UPLOAD,
    MAVEN_RESOLUTION,
    ANALYZING,
    VULNERABILITY_SCAN,
    REPORTING,
    COMPLETED,
    FAILED,
    CANCELLED
}

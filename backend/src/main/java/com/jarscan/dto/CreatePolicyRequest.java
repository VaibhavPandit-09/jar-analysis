package com.jarscan.dto;

import com.jarscan.model.PolicySeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record CreatePolicyRequest(
        @NotBlank String id,
        @NotBlank String name,
        String description,
        @NotBlank String ruleType,
        @NotNull PolicySeverity severity,
        Boolean enabled,
        Map<String, Object> config
) {
}

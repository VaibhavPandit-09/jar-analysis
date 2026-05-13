package com.jarscan.dto;

import com.jarscan.model.PolicySeverity;

import java.util.Map;

public record UpdatePolicyRequest(
        String name,
        String description,
        PolicySeverity severity,
        Boolean enabled,
        Map<String, Object> config
) {
}

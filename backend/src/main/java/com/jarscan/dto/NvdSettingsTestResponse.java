package com.jarscan.dto;

public record NvdSettingsTestResponse(
        boolean configured,
        boolean valid,
        String message
) {
}

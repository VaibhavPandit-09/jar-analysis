package com.jarscan.controller;

import com.jarscan.dto.NvdSettingsRequest;
import com.jarscan.dto.NvdSettingsStatusResponse;
import com.jarscan.dto.NvdSettingsTestResponse;
import com.jarscan.service.NvdSettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/settings/nvd", produces = MediaType.APPLICATION_JSON_VALUE)
public class SettingsController {

    private final NvdSettingsService nvdSettingsService;

    public SettingsController(NvdSettingsService nvdSettingsService) {
        this.nvdSettingsService = nvdSettingsService;
    }

    @GetMapping
    public NvdSettingsStatusResponse getNvdSettings() {
        return nvdSettingsService.getStatus();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public NvdSettingsStatusResponse saveNvdSettings(@RequestBody NvdSettingsRequest request) {
        return nvdSettingsService.save(request);
    }

    @PostMapping(path = "/test")
    public NvdSettingsTestResponse testNvdSettings() {
        return nvdSettingsService.testConfiguredKey();
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteNvdSettings() {
        nvdSettingsService.delete();
    }
}

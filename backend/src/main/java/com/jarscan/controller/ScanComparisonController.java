package com.jarscan.controller;

import com.jarscan.dto.ScanComparisonResponse;
import com.jarscan.service.ScanComparisonService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/compare", produces = MediaType.APPLICATION_JSON_VALUE)
public class ScanComparisonController {

    private final ScanComparisonService scanComparisonService;

    public ScanComparisonController(ScanComparisonService scanComparisonService) {
        this.scanComparisonService = scanComparisonService;
    }

    @GetMapping
    public ScanComparisonResponse compareScans(
            @RequestParam("base") String baselineScanId,
            @RequestParam("target") String targetScanId
    ) {
        return scanComparisonService.compareScans(baselineScanId, targetScanId);
    }
}

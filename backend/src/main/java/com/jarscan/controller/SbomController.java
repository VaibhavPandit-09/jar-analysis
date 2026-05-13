package com.jarscan.controller;

import com.jarscan.dto.AnalysisResult;
import com.jarscan.dto.SbomImportResponse;
import com.jarscan.dto.StoredScanSummaryResponse;
import com.jarscan.model.InputType;
import com.jarscan.service.SbomService;
import com.jarscan.service.ScanHistoryService;
import com.jarscan.util.HashUtils;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping(path = "/api/sbom", produces = MediaType.APPLICATION_JSON_VALUE)
public class SbomController {

    private final SbomService sbomService;
    private final ScanHistoryService scanHistoryService;

    public SbomController(SbomService sbomService, ScanHistoryService scanHistoryService) {
        this.sbomService = sbomService;
        this.scanHistoryService = scanHistoryService;
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SbomImportResponse importSbom(@RequestParam("file") MultipartFile file) throws IOException {
        AnalysisResult result = sbomService.importCycloneDx(file);
        StoredScanSummaryResponse stored = scanHistoryService.persistImportedScan(
                result,
                InputType.SBOM,
                file.getOriginalFilename(),
                HashUtils.sha256(file.getBytes())
        );
        return new SbomImportResponse(stored.scanId(), stored.jobId());
    }
}

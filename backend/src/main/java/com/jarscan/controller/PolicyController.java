package com.jarscan.controller;

import com.jarscan.dto.CreatePolicyRequest;
import com.jarscan.dto.PolicyEvaluation;
import com.jarscan.dto.PolicyRecord;
import com.jarscan.dto.StoredScanResponse;
import com.jarscan.dto.UpdatePolicyRequest;
import com.jarscan.service.PolicyService;
import com.jarscan.service.ScanHistoryService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(path = "/api/policies", produces = MediaType.APPLICATION_JSON_VALUE)
public class PolicyController {

    private final PolicyService policyService;
    private final ScanHistoryService scanHistoryService;

    public PolicyController(PolicyService policyService, ScanHistoryService scanHistoryService) {
        this.policyService = policyService;
        this.scanHistoryService = scanHistoryService;
    }

    @GetMapping
    public List<PolicyRecord> listPolicies() {
        return policyService.listPolicies();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public PolicyRecord createPolicy(@Valid @RequestBody CreatePolicyRequest request) {
        return policyService.createPolicy(request);
    }

    @PatchMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PolicyRecord updatePolicy(@PathVariable String id, @RequestBody UpdatePolicyRequest request) {
        return policyService.updatePolicy(id, request);
    }

    @DeleteMapping("/{id}")
    public void deletePolicy(@PathVariable String id) {
        policyService.deletePolicy(id);
    }

    @PostMapping("/evaluate/{scanId}")
    public PolicyEvaluation evaluatePolicies(@PathVariable String scanId) {
        StoredScanResponse storedScan = scanHistoryService.getStoredScan(scanId);
        return storedScan.result() == null ? null : storedScan.result().policyEvaluation();
    }
}

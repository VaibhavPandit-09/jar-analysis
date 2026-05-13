package com.jarscan.service;

import com.jarscan.dto.AnalysisResult;
import org.springframework.stereotype.Service;

@Service
public class ScanResultViewService {

    private final SuppressionService suppressionService;
    private final PolicyService policyService;

    public ScanResultViewService(SuppressionService suppressionService, PolicyService policyService) {
        this.suppressionService = suppressionService;
        this.policyService = policyService;
    }

    public AnalysisResult decorate(AnalysisResult rawResult) {
        AnalysisResult withDomainSuppressions = suppressionService.applyDomainSuppressions(rawResult);
        AnalysisResult withPolicies = policyService.applyPolicies(withDomainSuppressions);
        AnalysisResult withPolicySuppressions = suppressionService.applyPolicySuppressions(withPolicies);
        return policyService.refreshPolicySummary(withPolicySuppressions);
    }
}

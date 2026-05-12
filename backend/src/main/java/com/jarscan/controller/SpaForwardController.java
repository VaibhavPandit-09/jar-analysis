package com.jarscan.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    @GetMapping({"/scan-history", "/jobs/{jobId}", "/jobs/{jobId}/results", "/scans/{scanId}/results"})
    public String forwardSpaRoutes() {
        return "forward:/index.html";
    }
}

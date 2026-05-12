package com.jarscan.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    @GetMapping({"/jobs/{jobId}", "/jobs/{jobId}/results"})
    public String forwardSpaRoutes() {
        return "forward:/index.html";
    }
}

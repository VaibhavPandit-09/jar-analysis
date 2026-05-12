package com.jarscan.controller;

import com.jarscan.config.JarScanProperties;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping(path = "/api/app", produces = MediaType.APPLICATION_JSON_VALUE)
public class AppInfoController {

    private final JarScanProperties properties;

    public AppInfoController(JarScanProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of(
                "name", "JARScan",
                "timestamp", Instant.now(),
                "mavenDependencyScope", properties.mavenDependencyScope(),
                "maxNestedJarDepth", properties.maxNestedJarDepth()
        );
    }
}

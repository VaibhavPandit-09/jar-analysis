package com.jarscan.service;

import com.jarscan.job.AnalysisJob;
import com.jarscan.maven.MavenResolutionResult;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

@Service
public class MavenResolutionService {

    public MavenResolutionResult resolveDependencies(AnalysisJob job, Path pomPath, String scope) {
        job.warnings().add("POM dependency resolution is configured but not yet active in this milestone");
        return new MavenResolutionResult(List.of(), "Dependency resolution pending implementation");
    }
}

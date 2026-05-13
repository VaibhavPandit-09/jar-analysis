package com.jarscan.controller;

import com.jarscan.dto.CreateSuppressionRequest;
import com.jarscan.dto.SuppressionRecord;
import com.jarscan.dto.UpdateSuppressionRequest;
import com.jarscan.service.SuppressionService;
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
@RequestMapping(path = "/api/suppressions", produces = MediaType.APPLICATION_JSON_VALUE)
public class SuppressionController {

    private final SuppressionService suppressionService;

    public SuppressionController(SuppressionService suppressionService) {
        this.suppressionService = suppressionService;
    }

    @GetMapping
    public List<SuppressionRecord> listSuppressions() {
        return suppressionService.listSuppressions();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public SuppressionRecord createSuppression(@Valid @RequestBody CreateSuppressionRequest request) {
        return suppressionService.createSuppression(request);
    }

    @PatchMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public SuppressionRecord updateSuppression(@PathVariable String id, @RequestBody UpdateSuppressionRequest request) {
        return suppressionService.updateSuppression(id, request);
    }

    @DeleteMapping("/{id}")
    public void deleteSuppression(@PathVariable String id) {
        suppressionService.deleteSuppression(id);
    }
}

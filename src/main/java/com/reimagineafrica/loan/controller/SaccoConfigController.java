package com.reimagineafrica.loan.controller;

import com.reimagineafrica.loan.entity.SaccoConfig;
import com.reimagineafrica.loan.service.SaccoConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/sacco-configs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SaccoConfigController {

    private final SaccoConfigService saccoConfigService;

    @GetMapping
    public ResponseEntity<List<SaccoConfig>> getAll() {
        return ResponseEntity.ok(saccoConfigService.getAll());
    }

    @GetMapping("/{saccoCode}")
    public ResponseEntity<SaccoConfig> getByCode(@PathVariable String saccoCode) {
        return ResponseEntity.ok(saccoConfigService.getByCode(saccoCode));
    }

    @PostMapping
    public ResponseEntity<SaccoConfig> create(@Valid @RequestBody SaccoConfig config) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(saccoConfigService.create(config));
    }

    @PutMapping("/{saccoCode}")
    public ResponseEntity<SaccoConfig> update(
            @PathVariable String saccoCode,
            @Valid @RequestBody SaccoConfig config,
            @RequestHeader("X-User-Id") String updatedBy) {
        return ResponseEntity.ok(saccoConfigService.update(saccoCode, config, updatedBy));
    }

    @DeleteMapping("/{saccoCode}/deactivate")
    public ResponseEntity<Void> deactivate(
            @PathVariable String saccoCode,
            @RequestHeader("X-User-Id") String updatedBy) {
        saccoConfigService.deactivate(saccoCode, updatedBy);
        return ResponseEntity.noContent().build();
    }

    /**
     * Preview what config would be resolved for a given SACCO code.
     * Useful for testing — shows fallback to defaults if not configured.
     */
    @GetMapping("/{saccoCode}/resolve")
    public ResponseEntity<SaccoConfig> resolve(@PathVariable String saccoCode) {
        return ResponseEntity.ok(saccoConfigService.resolveConfig(saccoCode));
    }
}

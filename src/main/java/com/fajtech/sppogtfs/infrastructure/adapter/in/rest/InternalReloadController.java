package com.fajtech.sppogtfs.infrastructure.adapter.in.rest;

import com.fajtech.sppogtfs.application.port.in.ReloadIndexPort;
import com.fajtech.sppogtfs.application.port.in.ReloadIndexPort.ReloadResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin endpoint to force an atomic index rebuild (§6). Guarded by the admin API key
 * (see {@link com.fajtech.sppogtfs.infrastructure.config.ApiKeyFilter}).
 */
@RestController
@RequestMapping("/internal")
public class InternalReloadController {

    private final ReloadIndexPort reload;

    public InternalReloadController(ReloadIndexPort reload) {
        this.reload = reload;
    }

    @PostMapping("/reload")
    public ResponseEntity<ReloadResult> reload() {
        return ResponseEntity.ok(reload.reload());
    }
}

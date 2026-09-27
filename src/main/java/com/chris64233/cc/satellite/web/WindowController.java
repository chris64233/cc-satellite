package com.chris64233.cc.satellite.web;

import com.chris64233.cc.satellite.service.CatalogService;
import com.chris64233.cc.satellite.web.dto.CreateWindowRequest;
import com.chris64233.cc.satellite.web.dto.WindowResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/windows")
public class WindowController {

    private final CatalogService catalogService;

    public WindowController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @PostMapping
    public ResponseEntity<WindowResponse> create(@Valid @RequestBody CreateWindowRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(WindowResponse.from(catalogService.createWindow(request)));
    }
}

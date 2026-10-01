package com.example.controller;

import com.example.dto.AdminServiceResponse;
import com.example.dto.ServiceRequestDTO;
import com.example.service.ServiceCatalogService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The admin's services page. Only an admin reaches it: SecurityConfig locks
 * all of /api/admin/** to that role, so there is no security code here.
 *
 * One PATCH covers editing, deactivating and reactivating, because the
 * frontend's request helper has no PUT.
 */
@RestController
@RequestMapping("/api/admin/services")
public class AdminServiceController {

    private final ServiceCatalogService catalogService;

    public AdminServiceController(ServiceCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public List<AdminServiceResponse> list() {
        return catalogService.list();
    }

    @PostMapping
    public ResponseEntity<AdminServiceResponse> create(@RequestBody ServiceRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogService.create(request));
    }

    @PatchMapping("/{serviceId}")
    public AdminServiceResponse update(@PathVariable Integer serviceId,
            @RequestBody ServiceRequestDTO request) {
        return catalogService.update(serviceId, request);
    }

    @DeleteMapping("/{serviceId}")
    public ResponseEntity<Void> delete(@PathVariable Integer serviceId) {
        catalogService.delete(serviceId);
        return ResponseEntity.noContent().build();
    }
}

package com.pwj.tracker.controller;

import com.pwj.tracker.dto.ApiResponse;
import com.pwj.tracker.dto.VendorRequest;
import com.pwj.tracker.model.Vendor;
import com.pwj.tracker.service.VendorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/vendors")
@RequiredArgsConstructor
public class VendorController {

    private final VendorService vendorService;

    /** GET /api/v1/vendors — All roles: vendor list */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Vendor>>> getVendors() {
        return ResponseEntity.ok(ApiResponse.ok("Vendors fetched", vendorService.getVendors()));
    }

    /** GET /api/v1/vendors/all — VP/Procurement: all vendors with any status */
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<Vendor>>> getAllVendorsWithStatus() {
        return ResponseEntity.ok(ApiResponse.ok("All vendors fetched", vendorService.getAllVendorsWithStatus()));
    }

    /** GET /api/v1/vendors/pending — VP only: list vendors awaiting approval */
    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<Vendor>>> getPendingVendors() {
        return ResponseEntity.ok(ApiResponse.ok("Pending vendors fetched", vendorService.getPendingVendors()));
    }

    /** PUT /api/v1/vendors/{id}/approve — VP only */
    @PutMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<Vendor>> approveVendor(@PathVariable Long id) {
        Vendor approved = vendorService.approveVendor(id);
        return ResponseEntity.ok(ApiResponse.ok("Vendor approved", approved));
    }

    /** PUT /api/v1/vendors/{id}/reject — VP only */
    @PutMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<Vendor>> rejectVendor(@PathVariable Long id) {
        Vendor rejected = vendorService.rejectVendor(id);
        return ResponseEntity.ok(ApiResponse.ok("Vendor rejected", rejected));
    }

    /** POST /api/v1/vendors — Procurement / Admin: add new vendor */
    @PostMapping
    public ResponseEntity<ApiResponse<Vendor>> createVendor(
            @Valid @RequestBody VendorRequest req) {
        Vendor created = vendorService.createVendor(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Vendor created", created));
    }

    /** PUT /api/v1/vendors/{id} — Procurement / Admin: update vendor data */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Vendor>> updateVendor(
            @PathVariable Long id,
            @Valid @RequestBody VendorRequest req) {
        Vendor updated = vendorService.updateVendor(id, req);
        return ResponseEntity.ok(ApiResponse.ok("Vendor updated", updated));
    }

    /** GET /api/v1/vendors/by-name?name=... — fetch vendor details by name for document generation */
    @GetMapping("/by-name")
    public ResponseEntity<ApiResponse<Vendor>> getVendorByName(@RequestParam String name) {
        return vendorService.getVendorByName(name)
                .map(v -> ResponseEntity.ok(ApiResponse.ok("Vendor found", v)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Vendor not found: " + name)));
    }

    /**
     * POST /api/v1/vendors/process-image
     * Body: { "imageUrl": "/api/v1/upload/image/<filename>" }
     * Calls Claude Vision API to extract vendor fields from the uploaded document.
     */
    @PostMapping("/process-image")
    public ResponseEntity<ApiResponse<Map<String, Object>>> processImage(
            @RequestBody Map<String, String> body) {
        String imageUrl = body.get("imageUrl");
        if (imageUrl == null || imageUrl.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("imageUrl is required"));
        }
        try {
            Map<String, Object> extracted = vendorService.processVendorImage(imageUrl);
            return ResponseEntity.ok(ApiResponse.ok("Data extracted successfully", extracted));
        } catch (Exception e) {
            log.error("Image processing failed for url={}: {}", imageUrl, e.getMessage());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ApiResponse.error("Could not extract data: " + e.getMessage()));
        }
    }
}

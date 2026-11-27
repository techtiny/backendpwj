package com.pwj.tracker.controller;

import com.pwj.tracker.dto.ApiResponse;
import com.pwj.tracker.model.OemContact;
import com.pwj.tracker.repository.OemContactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Procurement → OEM Database (next to Vendors): brand / manufacturer contacts by category and item. */
@RestController
@RequestMapping("/api/v1/oem")
@RequiredArgsConstructor
public class OemContactController {

    private final OemContactRepository repo;

    @GetMapping
    public ResponseEntity<ApiResponse<List<OemContact>>> list() {
        return ResponseEntity.ok(ApiResponse.ok("OEM contacts", repo.findAllByOrderByIdAsc()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OemContact>> create(@RequestBody Map<String, Object> b) {
        OemContact o = new OemContact();
        apply(o, b);
        if (blank(o.getBrand()) && blank(o.getItem()) && blank(o.getCategory()))
            return ResponseEntity.badRequest().body(ApiResponse.error("Enter at least a Category, Item or Brand"));
        o.setCreatedBy(str(b.get("username")));
        return ResponseEntity.ok(ApiResponse.ok("OEM contact added", repo.save(o)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<OemContact>> update(@PathVariable Long id, @RequestBody Map<String, Object> b) {
        OemContact o = repo.findById(id).orElse(null);
        if (o == null) return ResponseEntity.status(404).body(ApiResponse.error("OEM contact not found"));
        apply(o, b);
        return ResponseEntity.ok(ApiResponse.ok("OEM contact updated", repo.save(o)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        if (!repo.existsById(id)) return ResponseEntity.status(404).body(ApiResponse.error("OEM contact not found"));
        repo.deleteById(id);
        return ResponseEntity.ok(ApiResponse.ok("Deleted", null));
    }

    private static void apply(OemContact o, Map<String, Object> b) {
        if (b.containsKey("category")) o.setCategory(str(b.get("category")));
        if (b.containsKey("item")) o.setItem(str(b.get("item")));
        if (b.containsKey("brand")) o.setBrand(str(b.get("brand")));
        if (b.containsKey("contactPerson")) o.setContactPerson(str(b.get("contactPerson")));
        if (b.containsKey("designation")) o.setDesignation(str(b.get("designation")));
        if (b.containsKey("contactNumber")) o.setContactNumber(str(b.get("contactNumber")));
        if (b.containsKey("contactEmail")) o.setContactEmail(str(b.get("contactEmail")));
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    private static String str(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }
}

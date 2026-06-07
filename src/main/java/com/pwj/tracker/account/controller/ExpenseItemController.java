package com.pwj.tracker.account.controller;

import com.pwj.tracker.account.dto.ExpenseItemDto;
import com.pwj.tracker.account.service.ExpenseItemService;
import com.pwj.tracker.repository.ProjectRepository;
import com.pwj.tracker.service.PwjEntryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.PatchMapping;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseItemController {

    private final ExpenseItemService service;
    private final PwjEntryService pwjEntryService;
    private final ProjectRepository projectRepo;

    public ExpenseItemController(ExpenseItemService service, PwjEntryService pwjEntryService,
                                  ProjectRepository projectRepo) {
        this.service         = service;
        this.pwjEntryService = pwjEntryService;
        this.projectRepo     = projectRepo;
    }

    @GetMapping("/{projectId}/{category}")
    public ResponseEntity<List<ExpenseItemDto>> getItems(
            @PathVariable Long projectId, @PathVariable String category) {
        return ResponseEntity.ok(service.getByProjectAndCategory(projectId, category));
    }

    @GetMapping("/{projectId}/{category}/summary")
    public ResponseEntity<Map<String, Object>> getSummary(
            @PathVariable Long projectId, @PathVariable String category) {
        return ResponseEntity.ok(service.getSummary(projectId, category));
    }

    @PostMapping
    public ResponseEntity<ExpenseItemDto> create(@RequestBody ExpenseItemDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ExpenseItemDto> update(@PathVariable Long id, @RequestBody ExpenseItemDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/pwj-docs")
    public ResponseEntity<List<Map<String, Object>>> getPwjDocs(
            @RequestParam(required = false) Long projectId) {
        List<Map<String, Object>> all = pwjEntryService.getDocSummaries();
        if (projectId == null) return ResponseEntity.ok(all);

        String projectName = projectRepo.findById(projectId)
                .map(p -> p.getName())
                .orElse(null);
        if (projectName == null) return ResponseEntity.ok(List.of());

        final String name = projectName;
        List<Map<String, Object>> filtered = all.stream()
                .filter(d -> name.equalsIgnoreCase((String) d.get("projectName")))
                .collect(Collectors.toList());
        return ResponseEntity.ok(filtered);
    }

    @GetMapping("/{projectId}/tracked-refs")
    public ResponseEntity<List<String>> getTrackedRefs(@PathVariable Long projectId) {
        return ResponseEntity.ok(service.getTrackedRefs(projectId));
    }

    @PatchMapping("/{id}/move")
    public ResponseEntity<ExpenseItemDto> moveCategory(
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(service.moveCategory(id, body.get("category")));
    }

    @PostMapping("/repair-categories")
    public ResponseEntity<Map<String, Object>> repairCategories() {
        return ResponseEntity.ok(Map.of("fixed", service.repairCategories()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }
}

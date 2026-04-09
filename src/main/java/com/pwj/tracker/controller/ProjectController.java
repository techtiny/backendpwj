package com.pwj.tracker.controller;

import com.pwj.tracker.dto.ApiResponse;
import com.pwj.tracker.model.Project;
import com.pwj.tracker.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectRepository projectRepository;

    @GetMapping
    public ApiResponse<List<Project>> getAll() {
        return ApiResponse.ok("Projects fetched", projectRepository.findAllByOrderByNameAsc());
    }

    @GetMapping("/active")
    public ApiResponse<List<Project>> getActive() {
        return ApiResponse.ok("Active projects fetched", projectRepository.findByActiveTrueOrderByNameAsc());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Project> create(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "").trim();
        if (name.isBlank()) throw new RuntimeException("Project name is required");
        Project project = Project.builder()
                .name(name)
                .location(body.get("location"))
                .description(body.get("description"))
                .active(true)
                .build();
        return ApiResponse.ok("Project created", projectRepository.save(project));
    }

    @PutMapping("/{id}")
    public ApiResponse<Project> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Project not found: " + id));
        if (body.containsKey("name") && !body.get("name").isBlank())
            project.setName(body.get("name").trim());
        if (body.containsKey("location"))   project.setLocation(body.get("location"));
        if (body.containsKey("description")) project.setDescription(body.get("description"));
        if (body.containsKey("active"))
            project.setActive(Boolean.parseBoolean(body.get("active")));
        return ApiResponse.ok("Project updated", projectRepository.save(project));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Project not found: " + id));
        project.setActive(false);
        projectRepository.save(project);
        return ApiResponse.ok("Project deactivated", null);
    }
}

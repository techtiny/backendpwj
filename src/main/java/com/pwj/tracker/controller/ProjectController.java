package com.pwj.tracker.controller;

import com.pwj.tracker.dto.ApiResponse;
import com.pwj.tracker.dto.ProjectRequest;
import com.pwj.tracker.model.Project;
import com.pwj.tracker.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectRepository projectRepository;

    @GetMapping
    public ApiResponse<List<Project>> getAll() {
        return ApiResponse.ok("Projects fetched", projectRepository.findAllByOrderByNameAsc());
    }

    @GetMapping("/{id}")
    public ApiResponse<Project> getById(@PathVariable Long id) {
        return ApiResponse.ok("Project fetched", projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Project not found: " + id)));
    }

    @GetMapping("/active")
    public ApiResponse<List<Project>> getActive() {
        return ApiResponse.ok("Active projects fetched", projectRepository.findByActiveTrueOrderByNameAsc());
    }

    /** Returns distinct clients (name + gstNo + address) from existing projects for autocomplete */
    @GetMapping("/clients")
    public ApiResponse<List<Map<String, String>>> getClients() {
        List<Map<String, String>> clients = projectRepository.findAllByOrderByNameAsc().stream()
                .filter(p -> p.getClientName() != null && !p.getClientName().isBlank())
                .collect(Collectors.toMap(
                        Project::getClientName,
                        p -> {
                            Map<String, String> m = new LinkedHashMap<>();
                            m.put("clientName",    p.getClientName());
                            m.put("clientGstNo",   p.getClientGstNo()   != null ? p.getClientGstNo()   : "");
                            m.put("clientAddress", p.getClientAddress() != null ? p.getClientAddress() : "");
                            return m;
                        },
                        (a, b) -> a   // keep first occurrence
                ))
                .values().stream()
                .sorted(Comparator.comparing(m -> m.get("clientName")))
                .collect(Collectors.toList());
        return ApiResponse.ok("Clients fetched", clients);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Project> create(@RequestBody ProjectRequest req) {
        if (req.getName() == null || req.getName().isBlank())
            throw new RuntimeException("Project name is required");

        Project project = Project.builder()
                .name(req.getName().trim())
                .location(req.getLocation())
                .description(req.getDescription())
                .clientName(req.getClientName())
                .clientGstNo(req.getClientGstNo())
                .clientAddress(req.getClientAddress())
                .billingAddress(req.getBillingAddress())
                .projectValue(req.getProjectValue())
                .quoteValue(req.getQuoteValue())
                .quoteGstPct(req.getQuoteGstPct())
                .quoteDocUrl(req.getQuoteDocUrl())
                .quoteTotalValue(computeTotal(req.getQuoteValue(), req.getQuoteGstPct()))
                .additionalWoValue(req.getAdditionalWoValue())
                .additionalWoGstPct(req.getAdditionalWoGstPct())
                .additionalWoTotal(computeTotal(req.getAdditionalWoValue(), req.getAdditionalWoGstPct()))
                .additionalWoDocUrl(req.getAdditionalWoDocUrl())
                .additionalQuoteValue(req.getAdditionalQuoteValue())
                .additionalQuoteGstPct(req.getAdditionalQuoteGstPct())
                .additionalQuoteTotal(computeTotal(req.getAdditionalQuoteValue(), req.getAdditionalQuoteGstPct()))
                .additionalQuoteDocUrl(req.getAdditionalQuoteDocUrl())
                .gstPct(req.getGstPct())
                .totalValue(computeTotal(req.getProjectValue(), req.getGstPct()))
                .poWoStatus(req.getPoWoStatus())
                .poWoDocUrl(req.getPoWoDocUrl())
                .amendedPoWoStatus(req.getAmendedPoWoStatus() != null ? req.getAmendedPoWoStatus() : "N/A")
                .amendedPoWoDocUrl(req.getAmendedPoWoDocUrl())
                .active(true)
                .build();
        return ApiResponse.ok("Project created", projectRepository.save(project));
    }

    @PutMapping("/{id}")
    public ApiResponse<Project> update(@PathVariable Long id, @RequestBody ProjectRequest req) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Project not found: " + id));

        if (req.getName() != null && !req.getName().isBlank())
            project.setName(req.getName().trim());
        if (req.getLocation()         != null) project.setLocation(req.getLocation());
        if (req.getDescription()      != null) project.setDescription(req.getDescription());
        if (req.getClientName()       != null) project.setClientName(req.getClientName());
        if (req.getClientGstNo()      != null) project.setClientGstNo(req.getClientGstNo());
        if (req.getClientAddress()    != null) project.setClientAddress(req.getClientAddress());
        if (req.getBillingAddress()   != null) project.setBillingAddress(req.getBillingAddress());
        if (req.getProjectValue()     != null) project.setProjectValue(req.getProjectValue());
        if (req.getQuoteValue()        != null) project.setQuoteValue(req.getQuoteValue());
        if (req.getQuoteGstPct()          != null) project.setQuoteGstPct(req.getQuoteGstPct());
        if (req.getQuoteDocUrl()          != null) project.setQuoteDocUrl(req.getQuoteDocUrl());
        if (req.getAdditionalWoValue()       != null) project.setAdditionalWoValue(req.getAdditionalWoValue());
        if (req.getAdditionalWoGstPct()      != null) project.setAdditionalWoGstPct(req.getAdditionalWoGstPct());
        if (req.getAdditionalWoDocUrl()      != null) project.setAdditionalWoDocUrl(req.getAdditionalWoDocUrl());
        if (req.getAdditionalWoValue() != null || req.getAdditionalWoGstPct() != null) {
            BigDecimal av = req.getAdditionalWoValue()   != null ? req.getAdditionalWoValue()   : project.getAdditionalWoValue();
            Integer    ag = req.getAdditionalWoGstPct()  != null ? req.getAdditionalWoGstPct()  : project.getAdditionalWoGstPct();
            if (av != null) project.setAdditionalWoTotal(computeTotal(av, ag));
        }
        if (req.getAdditionalQuoteValue()    != null) project.setAdditionalQuoteValue(req.getAdditionalQuoteValue());
        if (req.getAdditionalQuoteGstPct()   != null) project.setAdditionalQuoteGstPct(req.getAdditionalQuoteGstPct());
        if (req.getAdditionalQuoteDocUrl()   != null) project.setAdditionalQuoteDocUrl(req.getAdditionalQuoteDocUrl());
        if (req.getAdditionalQuoteValue() != null || req.getAdditionalQuoteGstPct() != null) {
            BigDecimal aqv = req.getAdditionalQuoteValue()  != null ? req.getAdditionalQuoteValue()  : project.getAdditionalQuoteValue();
            Integer    aqg = req.getAdditionalQuoteGstPct() != null ? req.getAdditionalQuoteGstPct() : project.getAdditionalQuoteGstPct();
            if (aqv != null) project.setAdditionalQuoteTotal(computeTotal(aqv, aqg));
        }
        BigDecimal qv = req.getQuoteValue() != null ? req.getQuoteValue() : project.getQuoteValue();
        Integer qg    = req.getQuoteGstPct() != null ? req.getQuoteGstPct() : project.getQuoteGstPct();
        if (qv != null) project.setQuoteTotalValue(computeTotal(qv, qg));
        if (req.getGstPct()           != null) project.setGstPct(req.getGstPct());
        if (req.getProjectValue() != null || req.getGstPct() != null) {
            BigDecimal val = req.getProjectValue() != null ? req.getProjectValue() : project.getProjectValue();
            Integer pct    = req.getGstPct()       != null ? req.getGstPct()       : project.getGstPct();
            project.setTotalValue(computeTotal(val, pct));
        }
        if (req.getPoWoStatus()          != null) project.setPoWoStatus(req.getPoWoStatus());
        if (req.getPoWoDocUrl()          != null) project.setPoWoDocUrl(req.getPoWoDocUrl());
        if (req.getAmendedPoWoStatus()   != null) project.setAmendedPoWoStatus(req.getAmendedPoWoStatus());
        if (req.getAmendedPoWoDocUrl()   != null) project.setAmendedPoWoDocUrl(req.getAmendedPoWoDocUrl());
        if (req.getActive()              != null) project.setActive(req.getActive());

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

    /** DELETE /api/v1/projects/{id}/permanent — Admin/VP: permanently delete a project */
    @DeleteMapping("/{id}/permanent")
    public ApiResponse<Void> permanentDelete(@PathVariable Long id) {
        if (!projectRepository.existsById(id)) throw new RuntimeException("Project not found: " + id);
        projectRepository.deleteById(id);
        return ApiResponse.ok("Project permanently deleted", null);
    }

    // ── helpers ──────────────────────────────────────────────────────────────
    private BigDecimal computeTotal(BigDecimal value, Integer gstPct) {
        if (value == null) return null;
        if (gstPct == null || gstPct == 0) return value;
        BigDecimal gst = value.multiply(BigDecimal.valueOf(gstPct)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return value.add(gst).setScale(2, RoundingMode.HALF_UP);
    }
}

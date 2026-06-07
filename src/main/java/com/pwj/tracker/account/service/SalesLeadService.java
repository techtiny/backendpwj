package com.pwj.tracker.account.service;

import com.pwj.tracker.account.dto.SalesLeadDto;
import com.pwj.tracker.account.entity.SalesLead;
import com.pwj.tracker.account.repository.SalesLeadRepository;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SalesLeadService {

    private static final List<String> STAGES =
            List.of("PROSPECT", "QUALIFIED", "PROPOSAL", "NEGOTIATION", "WON", "LOST");

    private final SalesLeadRepository repository;

    public SalesLeadService(SalesLeadRepository repository) {
        this.repository = repository;
    }

    public List<SalesLeadDto> getAll() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(this::toDto).collect(Collectors.toList());
    }

    public SalesLeadDto getById(Long id) {
        return repository.findById(id).map(this::toDto)
                .orElseThrow(() -> new IllegalArgumentException("Sales lead not found: " + id));
    }

    public SalesLeadDto create(SalesLeadDto dto) {
        SalesLead lead = new SalesLead();
        applyDto(lead, dto);
        lead.setId(null);
        if (lead.getStage() == null || lead.getStage().isBlank()) lead.setStage("PROSPECT");
        if (lead.getFy() == null) lead.setFy(currentFy());
        return toDto(repository.save(lead));
    }

    public SalesLeadDto update(Long id, SalesLeadDto dto) {
        SalesLead existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sales lead not found: " + id));
        applyDto(existing, dto);
        return toDto(repository.save(existing));
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) throw new IllegalArgumentException("Sales lead not found: " + id);
        repository.deleteById(id);
    }

    public Map<String, Object> getSummary() {
        long total = repository.count();
        long won   = repository.countByStage("WON");
        long lost  = repository.countByStage("LOST");
        long active = total - won - lost;
        BigDecimal pipeline = repository.sumPipelineValue();
        BigDecimal wonValue  = repository.sumWonValue();

        Map<String, Object> stageBreakdown = new LinkedHashMap<>();
        for (String stage : STAGES) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("count", repository.countByStage(stage));
            s.put("value", repository.sumDealValueByStage(stage));
            stageBreakdown.put(stage, s);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total",          total);
        result.put("active",         active);
        result.put("won",            won);
        result.put("lost",           lost);
        result.put("pipelineValue",  pipeline);
        result.put("wonValue",       wonValue);
        result.put("conversionRate", total > 0 ? (double) won / total * 100 : 0.0);
        result.put("stages",         stageBreakdown);
        return result;
    }

    private String currentFy() {
        LocalDate today = LocalDate.now();
        int month = today.getMonthValue();
        int year  = today.getYear();
        int fyStart = month >= 4 ? year : year - 1;
        return String.format("%02d-%02d", fyStart % 100, (fyStart + 1) % 100);
    }

    private SalesLeadDto toDto(SalesLead s) {
        SalesLeadDto dto = new SalesLeadDto();
        dto.setId(s.getId()); dto.setTitle(s.getTitle()); dto.setClient(s.getClient());
        dto.setContactPerson(s.getContactPerson()); dto.setContactPhone(s.getContactPhone());
        dto.setContactEmail(s.getContactEmail()); dto.setStage(s.getStage()); dto.setSource(s.getSource());
        dto.setBusinessType(s.getBusinessType()); dto.setLocation(s.getLocation());
        dto.setDescription(s.getDescription()); dto.setNotes(s.getNotes());
        dto.setDealValue(safe(s.getDealValue())); dto.setQuoteValue(s.getQuoteValue());
        dto.setProbabilityPct(s.getProbabilityPct()); dto.setExpectedCloseDate(s.getExpectedCloseDate());
        dto.setActualCloseDate(s.getActualCloseDate()); dto.setAssignedTo(s.getAssignedTo());
        dto.setFy(s.getFy()); dto.setCreatedAt(s.getCreatedAt()); dto.setUpdatedAt(s.getUpdatedAt());
        return dto;
    }

    private void applyDto(SalesLead s, SalesLeadDto dto) {
        if (dto.getTitle() != null)         s.setTitle(dto.getTitle());
        if (dto.getClient() != null)        s.setClient(dto.getClient());
        if (dto.getContactPerson() != null) s.setContactPerson(dto.getContactPerson());
        if (dto.getContactPhone() != null)  s.setContactPhone(dto.getContactPhone());
        if (dto.getContactEmail() != null)  s.setContactEmail(dto.getContactEmail());
        if (dto.getStage() != null)         s.setStage(dto.getStage());
        if (dto.getSource() != null)        s.setSource(dto.getSource());
        if (dto.getBusinessType() != null)  s.setBusinessType(dto.getBusinessType());
        s.setLocation(dto.getLocation()); s.setDescription(dto.getDescription()); s.setNotes(dto.getNotes());
        if (dto.getDealValue() != null)     s.setDealValue(dto.getDealValue());
        s.setQuoteValue(dto.getQuoteValue()); s.setProbabilityPct(dto.getProbabilityPct());
        s.setExpectedCloseDate(dto.getExpectedCloseDate()); s.setActualCloseDate(dto.getActualCloseDate());
        if (dto.getAssignedTo() != null)    s.setAssignedTo(dto.getAssignedTo());
        if (dto.getFy() != null)            s.setFy(dto.getFy());
    }

    private BigDecimal safe(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}

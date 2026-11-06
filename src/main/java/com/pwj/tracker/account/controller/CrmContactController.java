package com.pwj.tracker.account.controller;

import com.pwj.tracker.account.entity.CrmContact;
import com.pwj.tracker.account.repository.CrmContactRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Sales CRM — People and Companies (kind = people | companies). */
@RestController
@RequestMapping("/api/crm")
public class CrmContactController {

    private final CrmContactRepository repo;

    public CrmContactController(CrmContactRepository repo) { this.repo = repo; }

    private static String kindOf(String path) {
        return switch (path.toLowerCase()) {
            case "people" -> "PERSON";
            case "companies" -> "COMPANY";
            default -> throw new IllegalArgumentException("Unknown CRM list: " + path);
        };
    }

    private static String clean(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private static void apply(CrmContact c, CrmContact in) {
        if (clean(in.getName()) == null) throw new IllegalArgumentException("Name is required");
        c.setName(in.getName().trim());
        c.setTag(clean(in.getTag()));
        c.setCompany(clean(in.getCompany()));
        c.setContactName(clean(in.getContactName()));
        c.setSource(clean(in.getSource()));
        c.setTitle(clean(in.getTitle()));
        c.setContactType(clean(in.getContactType()));
        c.setWorkPhone(clean(in.getWorkPhone()));
        c.setConnectBy(clean(in.getConnectBy()));
        c.setFollowedBy(clean(in.getFollowedBy()));
        c.setStatus(clean(in.getStatus()));
        c.setDescription(clean(in.getDescription()));
    }

    @GetMapping("/{list}")
    public List<CrmContact> list(@PathVariable String list) {
        return repo.findByKindOrderByIdAsc(kindOf(list));
    }

    @PostMapping("/{list}")
    public ResponseEntity<CrmContact> create(@PathVariable String list, @RequestBody CrmContact in) {
        CrmContact c = new CrmContact();
        c.setKind(kindOf(list));
        apply(c, in);
        return ResponseEntity.status(HttpStatus.CREATED).body(repo.save(c));
    }

    @PutMapping("/{list}/{id}")
    public CrmContact update(@PathVariable String list, @PathVariable Long id, @RequestBody CrmContact in) {
        CrmContact c = repo.findById(id).filter(x -> x.getKind().equals(kindOf(list)))
                .orElseThrow(() -> new IllegalArgumentException("Not found: " + id));
        apply(c, in);
        return repo.save(c);
    }

    @DeleteMapping("/{list}/{id}")
    public ResponseEntity<Void> delete(@PathVariable String list, @PathVariable Long id) {
        repo.findById(id).filter(x -> x.getKind().equals(kindOf(list))).ifPresent(repo::delete);
        return ResponseEntity.noContent().build();
    }
}

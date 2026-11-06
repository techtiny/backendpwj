package com.pwj.tracker.account.controller;

import com.pwj.tracker.account.entity.Campaign;
import com.pwj.tracker.account.entity.CampaignContact;
import com.pwj.tracker.account.service.CampaignService;
import com.pwj.tracker.account.service.WhatsAppGateway;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Sales → Campaigns: the campaign contact list, the WhatsApp (QR) connection, and festival-message campaigns.
 * Only OH, VP and CEO may use it, and each of those roles works on its own list — every call carries the caller's
 * username and the role is read from their account (see CampaignService.roleOf).
 */
@RestController
@RequestMapping("/api/campaign")
public class CampaignController {

    private final CampaignService service;
    private final WhatsAppGateway gateway;

    public CampaignController(CampaignService service, WhatsAppGateway gateway) { this.service = service; this.gateway = gateway; }

    // ── WhatsApp connection ──
    @GetMapping("/whatsapp/status")
    public Map<String, Object> status(@RequestParam String username) { service.roleOf(username); return gateway.status(); }

    @PostMapping("/whatsapp/logout")
    public ResponseEntity<Void> logout(@RequestParam String username) { service.roleOf(username); gateway.logout(); return ResponseEntity.noContent().build(); }

    /** The greeting card image, for the preview in the campaign form (an <img> can't send the username, and the card is not sensitive). */
    @GetMapping(value = "/card/{theme}.png", produces = "image/png")
    public ResponseEntity<byte[]> card(@PathVariable String theme) {
        byte[] png = gateway.card(theme);
        return png == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(png);
    }

    // ── Contacts ──
    @GetMapping("/contacts")
    public List<CampaignContact> contacts(@RequestParam String username) { return service.listContacts(service.roleOf(username)); }

    @PostMapping("/contacts")
    public ResponseEntity<CampaignContact> addContact(@RequestParam String username, @RequestBody CampaignContact in) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.saveContact(service.roleOf(username), null, in));
    }

    @PutMapping("/contacts/{id}")
    public CampaignContact updateContact(@RequestParam String username, @PathVariable Long id, @RequestBody CampaignContact in) {
        return service.saveContact(service.roleOf(username), id, in);
    }

    @DeleteMapping("/contacts/{id}")
    public ResponseEntity<Void> deleteContact(@RequestParam String username, @PathVariable Long id) {
        service.deleteContact(service.roleOf(username), id);
        return ResponseEntity.noContent().build();
    }

    /** POST /api/campaign/contacts/import — body: list = people | companies, ids = CRM row ids. */
    @PostMapping("/contacts/import")
    @SuppressWarnings("unchecked")
    public Map<String, Object> importContacts(@RequestParam String username, @RequestBody Map<String, Object> body) {
        List<Long> ids = ((List<Object>) body.getOrDefault("ids", List.of())).stream().map(o -> Long.valueOf(o.toString())).toList();
        return service.importFromCrm(service.roleOf(username), String.valueOf(body.get("list")), ids);
    }

    // ── Campaigns ──
    @GetMapping
    public List<Campaign> campaigns(@RequestParam String username) { return service.listCampaigns(service.roleOf(username)); }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@RequestParam String username, @PathVariable Long id) { return service.detail(service.roleOf(username), id); }

    /** POST /api/campaign — body: name, message, card, contactIds, createdBy. */
    @PostMapping
    @SuppressWarnings("unchecked")
    public ResponseEntity<Campaign> create(@RequestParam String username, @RequestBody Map<String, Object> body) {
        List<Long> ids = ((List<Object>) body.getOrDefault("contactIds", List.of())).stream().map(o -> Long.valueOf(o.toString())).toList();
        Object by = body.get("createdBy");
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(service.roleOf(username), (String) body.get("name"),
                (String) body.get("message"), (String) body.get("card"), ids, by == null ? null : by.toString()));
    }

    @PostMapping("/{id}/start")
    public Campaign start(@RequestParam String username, @PathVariable Long id) { return service.start(service.roleOf(username), id); }

    @PostMapping("/{id}/stop")
    public Campaign stop(@RequestParam String username, @PathVariable Long id) { return service.stop(service.roleOf(username), id); }

    @PostMapping("/{id}/retry-failed")
    public Campaign retryFailed(@RequestParam String username, @PathVariable Long id) { return service.retryFailed(service.roleOf(username), id); }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@RequestParam String username, @PathVariable Long id) {
        service.delete(service.roleOf(username), id);
        return ResponseEntity.noContent().build();
    }
}

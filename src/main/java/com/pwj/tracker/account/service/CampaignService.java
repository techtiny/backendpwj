package com.pwj.tracker.account.service;

import com.pwj.tracker.account.entity.*;
import com.pwj.tracker.account.repository.*;
import com.pwj.tracker.model.AppUser;
import com.pwj.tracker.repository.AppUserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/**
 * WhatsApp campaigns. Messages go out one at a time with a random pause between them — a burst of identical messages is
 * the quickest way to get a WhatsApp number banned. Sending runs in the background; the page polls the campaign for progress.
 */
@Service
public class CampaignService {

    static final int MIN_DELAY_S = 8, MAX_DELAY_S = 20;
    static final int MAX_RECIPIENTS = 250;
    static final Set<String> CARDS = Set.of("DIWALI");   // greeting cards the gateway can draw

    private final CampaignContactRepository contacts;
    private final CampaignRepository campaigns;
    private final CampaignRecipientRepository recipients;
    private final CrmContactRepository crm;
    private final WhatsAppGateway gateway;
    private final AppUserRepository users;

    private final ExecutorService worker = Executors.newCachedThreadPool();
    private final Set<Long> stopRequested = ConcurrentHashMap.newKeySet();
    private final Set<Long> running = ConcurrentHashMap.newKeySet();

    public CampaignService(CampaignContactRepository contacts, CampaignRepository campaigns, CampaignRecipientRepository recipients,
                           CrmContactRepository crm, WhatsAppGateway gateway, AppUserRepository users) {
        this.contacts = contacts; this.campaigns = campaigns; this.recipients = recipients; this.crm = crm; this.gateway = gateway; this.users = users;
    }

    /** A restart kills the sender thread — don't leave a campaign showing "Sending" forever. */
    @PostConstruct
    @Transactional
    void markInterrupted() {
        for (Campaign c : campaigns.findByStatus("SENDING")) {
            c.setStatus("STOPPED"); c.setNote("Interrupted by a server restart — press Resume to send the rest");
            campaigns.save(c);
        }
    }

    // ── Who may use this ─────────────────────────────────────────────────

    /** Campaigns are for OH, VP and CEO only; each role works on its own list. Returns that role (the list the caller owns). */
    public String roleOf(String username) {
        AppUser u = users.findByUsernameAndActiveTrue(username == null ? "" : username)
                .orElseThrow(() -> new IllegalArgumentException("Not signed in"));
        if (u.getRole() != AppUser.Role.OH && u.getRole() != AppUser.Role.VP && u.getRole() != AppUser.Role.CEO)
            throw new IllegalArgumentException("Campaigns are available to OH, VP and CEO only");
        return u.getRole().name();
    }

    // ── Contacts ─────────────────────────────────────────────────────────

    /** Digits with country code. A 10-digit number is taken as Indian (+91). Returns null when it can't be a phone number. */
    public static String normalizePhone(String raw) {
        if (raw == null) return null;
        String d = raw.replaceAll("\\D", "");
        if (d.startsWith("00")) d = d.substring(2);
        if (d.length() == 11 && d.startsWith("0")) d = d.substring(1);
        if (d.length() == 10) d = "91" + d;
        return d.matches("\\d{11,15}") ? d : null;
    }

    public List<CampaignContact> listContacts(String role) { return contacts.findByOwnerRoleOrderByNameAsc(role); }

    @Transactional
    public CampaignContact saveContact(String role, Long id, CampaignContact in) {
        if (in.getName() == null || in.getName().isBlank()) throw new IllegalArgumentException("Name is required");
        String phone = normalizePhone(in.getPhone());
        if (phone == null) throw new IllegalArgumentException("Enter a valid mobile number (with country code, or a 10-digit Indian number)");
        contacts.findByOwnerRoleAndPhone(role, phone).filter(x -> !x.getId().equals(id)).ifPresent(x -> { throw new IllegalArgumentException(x.getName() + " already has this number"); });
        CampaignContact c = id == null ? new CampaignContact() : ownedContact(role, id);
        c.setOwnerRole(role);
        c.setName(in.getName().trim()); c.setPhone(phone);
        c.setCompany(in.getCompany() == null || in.getCompany().isBlank() ? null : in.getCompany().trim());
        return contacts.save(c);
    }

    public void deleteContact(String role, Long id) { contacts.delete(ownedContact(role, id)); }

    private CampaignContact ownedContact(String role, Long id) {
        return contacts.findById(id).filter(c -> role.equals(c.getOwnerRole())).orElseThrow(() -> new IllegalArgumentException("Contact not found"));
    }

    /** Move People / Companies from the CRM lists into the campaign list. Rows with no valid phone, or already in the list, are skipped. */
    @Transactional
    public Map<String, Object> importFromCrm(String role, String list, List<Long> ids) {
        String kind = "companies".equals(list) ? "COMPANY" : "PERSON";
        int added = 0, noPhone = 0, duplicate = 0;
        for (CrmContact r : crm.findAllById(ids)) {
            if (!kind.equals(r.getKind())) continue;
            String phone = normalizePhone(r.getWorkPhone());
            if (phone == null) { noPhone++; continue; }
            if (contacts.findByOwnerRoleAndPhone(role, phone).isPresent()) { duplicate++; continue; }
            CampaignContact c = new CampaignContact();
            c.setName(kind.equals("COMPANY") && r.getContactName() != null ? r.getContactName() : r.getName());
            c.setCompany(kind.equals("COMPANY") ? r.getName() : r.getCompany());
            c.setPhone(phone); c.setOrigin(kind); c.setOwnerRole(role);
            contacts.save(c); added++;
        }
        return Map.of("added", added, "noPhone", noPhone, "duplicate", duplicate);
    }

    // ── Campaigns ────────────────────────────────────────────────────────

    public List<Campaign> listCampaigns(String role) { return campaigns.findByOwnerRoleOrderByCreatedAtDesc(role); }

    public Map<String, Object> detail(String role, Long id) {
        Campaign c = owned(role, id);
        List<CampaignRecipient> rs = recipients.findByCampaignIdOrderByIdAsc(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("campaign", c); m.put("recipients", rs);
        m.put("sent", rs.stream().filter(r -> "SENT".equals(r.getStatus())).count());
        m.put("failed", rs.stream().filter(r -> "FAILED".equals(r.getStatus())).count());
        m.put("pending", rs.stream().filter(r -> "PENDING".equals(r.getStatus())).count());
        return m;
    }

    @Transactional
    public Campaign create(String role, String name, String message, String card, List<Long> contactIds, String by) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Give the campaign a name, e.g. Diwali 2026");
        if (message == null || message.isBlank()) throw new IllegalArgumentException("Write the message");
        if (contactIds == null || contactIds.isEmpty()) throw new IllegalArgumentException("Select at least one contact");
        if (contactIds.size() > MAX_RECIPIENTS) throw new IllegalArgumentException("Send to at most " + MAX_RECIPIENTS + " contacts per campaign — split the list");
        Campaign c = new Campaign();
        if (card != null && !card.isBlank() && !CARDS.contains(card.toUpperCase())) throw new IllegalArgumentException("Unknown greeting card");
        c.setOwnerRole(role);
        c.setName(name.trim()); c.setMessage(message.trim()); c.setCreatedBy(by);
        c.setCard(card == null || card.isBlank() ? null : card.toUpperCase());
        c = campaigns.save(c);
        for (CampaignContact k : contacts.findAllById(contactIds)) {
            if (!role.equals(k.getOwnerRole())) continue;   // only this role's own contacts
            CampaignRecipient r = new CampaignRecipient();
            r.setCampaignId(c.getId()); r.setName(k.getName()); r.setPhone(k.getPhone());
            recipients.save(r);
        }
        return c;
    }

    @Transactional
    public void delete(String role, Long id) {
        owned(role, id);
        if (running.contains(id)) throw new IllegalArgumentException("Stop the campaign before deleting it");
        recipients.deleteByCampaignId(id);
        campaigns.deleteById(id);
    }

    /** Start, or resume (only the recipients still pending are sent — nobody is messaged twice). */
    public Campaign start(String role, Long id) {
        Campaign c = owned(role, id);
        if (running.contains(id)) throw new IllegalArgumentException("Already sending");
        if (!gateway.connected()) throw new IllegalArgumentException("WhatsApp isn't connected — scan the QR code first");
        if (recipients.countByCampaignIdAndStatus(id, "PENDING") == 0) throw new IllegalArgumentException("Nobody is left to send to");
        c.setStatus("SENDING"); c.setNote(null); c.setFinishedAt(null);
        if (c.getStartedAt() == null) c.setStartedAt(LocalDateTime.now());
        campaigns.save(c);
        stopRequested.remove(id); running.add(id);
        worker.submit(() -> runCampaign(id));
        return c;
    }

    @Transactional
    public Campaign stop(String role, Long id) {
        Campaign c = owned(role, id);
        if (running.contains(id)) stopRequested.add(id);
        return c;
    }

    /** Re-queue the failed recipients of a campaign so Resume sends them again. */
    @Transactional
    public Campaign retryFailed(String role, Long id) {
        Campaign c = owned(role, id);
        if (running.contains(id)) throw new IllegalArgumentException("Wait until the campaign stops");
        for (CampaignRecipient r : recipients.findByCampaignIdAndStatusOrderByIdAsc(id, "FAILED")) { r.setStatus("PENDING"); r.setError(null); recipients.save(r); }
        return c;
    }

    private void runCampaign(Long id) {
        String stopNote = null;
        try {
            Campaign c = get(id);
            for (CampaignRecipient r : recipients.findByCampaignIdAndStatusOrderByIdAsc(id, "PENDING")) {
                if (stopRequested.contains(id)) { stopNote = "Stopped by you"; break; }
                if (!gateway.connected()) { stopNote = "WhatsApp got disconnected — reconnect and press Resume"; break; }
                try {
                    gateway.send(r.getPhone(), personalise(c.getMessage(), r.getName()), c.getCard());
                    r.setStatus("SENT"); r.setSentAt(LocalDateTime.now()); r.setError(null);
                } catch (RuntimeException e) {
                    r.setStatus("FAILED"); r.setError(e.getMessage());
                }
                recipients.save(r);
                sleepInterruptibly(id, (MIN_DELAY_S + ThreadLocalRandom.current().nextInt(MAX_DELAY_S - MIN_DELAY_S + 1)) * 1000L);
            }
        } catch (Exception e) {
            stopNote = "Stopped: " + e.getMessage();
        } finally {
            Campaign c = get(id);
            boolean left = recipients.countByCampaignIdAndStatus(id, "PENDING") > 0;
            c.setStatus(left ? "STOPPED" : "DONE"); c.setNote(left ? stopNote : null);
            if (!left) c.setFinishedAt(LocalDateTime.now());
            campaigns.save(c);
            running.remove(id); stopRequested.remove(id);
        }
    }

    private void sleepInterruptibly(Long id, long ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end && !stopRequested.contains(id)) {
            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
    }

    /** {name} → the contact's first name. */
    static String personalise(String message, String name) {
        String first = name == null ? "" : name.trim().split("\\s+")[0];
        return message.replace("{name}", first);
    }

    private Campaign owned(String role, Long id) {
        Campaign c = get(id);
        if (!role.equals(c.getOwnerRole())) throw new IllegalArgumentException("Campaign not found");
        return c;
    }

    private Campaign get(Long id) { return campaigns.findById(id).orElseThrow(() -> new IllegalArgumentException("Campaign not found")); }
}

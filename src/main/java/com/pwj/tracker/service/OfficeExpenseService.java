package com.pwj.tracker.service;

import com.pwj.tracker.model.AppUser;
import com.pwj.tracker.model.OfficeExpense;
import com.pwj.tracker.model.OfficeStaff;
import com.pwj.tracker.model.Employee;
import com.pwj.tracker.repository.AppUserRepository;
import com.pwj.tracker.repository.EmployeeRepository;
import com.pwj.tracker.repository.OfficeExpenseRepository;
import com.pwj.tracker.repository.OfficeStaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * HR → Office/Mgnt: office / management expenses raised by a VP / Admin, plus a monthly SALARY line for every active
 * office staff member (created automatically for each month from their start month up to the current month).
 * Everything is paid from Accounts → Weekly Payments → Office/Mgnt.
 */
@Service
@RequiredArgsConstructor
public class OfficeExpenseService {

    private static final Set<AppUser.Role> MANAGERS = EnumSet.of(AppUser.Role.ADMIN, AppUser.Role.VP);
    private static final Set<AppUser.Role> VIEWERS  = EnumSet.of(AppUser.Role.ADMIN, AppUser.Role.VP, AppUser.Role.OH, AppUser.Role.CEO);
    private static final BigDecimal DEFAULT_SALARY = new BigDecimal("4500");

    private final OfficeExpenseRepository repo;
    private final OfficeStaffRepository staffRepo;
    private final AppUserRepository userRepo;
    private final EmployeeRepository employeeRepo;

    /** First run: the office staff paid ₹4,500 a month each, starting this month. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedStaff() {
        if (staffRepo.count() > 0) return;
        String start = YearMonth.now().toString();
        for (String[] s : new String[][] { { "Anusha", "VP" }, { "Bharath", "OH" }, { "Sunil", "CEO" }, { "Chinna Ponnu", "Office Staff" } }) {
            staffRepo.save(OfficeStaff.builder().name(s[0]).designation(s[1]).monthlyAmount(DEFAULT_SALARY)
                    .startMonth(start).active(true).build());
        }
    }

    // ── Listing ────────────────────────────────────────────────────────────────────────────────────────────────

    /** month = YYYY-MM, or blank / ALL for every month; status = PENDING / PAID, or blank for both. */
    @Transactional
    public List<OfficeExpense> list(String actor, String month, String status) {
        viewer(actor);
        ensureSalaries();
        boolean allMonths = month == null || month.isBlank() || "ALL".equalsIgnoreCase(month);
        List<OfficeExpense> rows = allMonths ? repo.findAllByOrderByPeriodMonthDescKindDescIdAsc()
                                             : repo.findByPeriodMonthOrderByKindDescIdAsc(YearMonth.parse(month).toString());
        if (status != null && !status.isBlank()) rows = rows.stream().filter(r -> status.equalsIgnoreCase(r.getStatus())).toList();
        return rows;
    }

    /** Creates any missing SALARY line, for each active staff member, for every month from their start month to now. */
    @Transactional
    public void ensureSalaries() {
        YearMonth now = YearMonth.now();
        List<Employee> master = null;
        for (OfficeStaff s : staffRepo.findByActiveTrue()) {
            // Bank details come from the linked Employee Master record (linked by name the first time).
            if (master == null) master = employeeRepo.findAllByOrderByFullNameAsc();
            Employee emp = linkedEmployee(s, master);
            if (emp != null && !blank(emp.getBankAccountNumber())
                    && (!emp.getBankAccountNumber().equals(s.getAccountNumber()) || !java.util.Objects.equals(emp.getIfscCode(), s.getIfsc())
                        || !java.util.Objects.equals(emp.getBankName(), s.getBankName()))) {
                s.setBankName(emp.getBankName()); s.setAccountNumber(emp.getBankAccountNumber()); s.setIfsc(emp.getIfscCode());
                staffRepo.save(s);
            }
            for (YearMonth m = YearMonth.parse(s.getStartMonth()); !m.isAfter(now); m = m.plusMonths(1)) {
                if (repo.existsByKindAndStaffIdAndPeriodMonth("SALARY", s.getId(), m.toString())) continue;
                repo.save(OfficeExpense.builder().kind("SALARY").periodMonth(m.toString()).category("Salary")
                        .description("Salary – " + label(m)).payee(s.getName()).amount(s.getMonthlyAmount())
                        .staffId(s.getId()).bankName(s.getBankName()).accountNumber(s.getAccountNumber()).ifsc(s.getIfsc())
                        .status("PENDING").raisedBy("Auto (monthly)").build());
            }
        }
        syncSalaryBank();
    }

    /** Unpaid salary lines follow the staff member's current bank details. */
    private void syncSalaryBank() {
        java.util.Map<Long, OfficeStaff> byId = new java.util.HashMap<>();
        staffRepo.findAll().forEach(s -> byId.put(s.getId(), s));
        repo.findByStatusOrderByPeriodMonthDescKindDescIdAsc("PENDING").stream()
                .filter(e -> "SALARY".equals(e.getKind()) && byId.containsKey(e.getStaffId()))
                .forEach(e -> {
                    OfficeStaff s = byId.get(e.getStaffId());
                    if (!java.util.Objects.equals(e.getAccountNumber(), s.getAccountNumber()) || !java.util.Objects.equals(e.getIfsc(), s.getIfsc())
                            || !java.util.Objects.equals(e.getBankName(), s.getBankName())) {
                        e.setBankName(s.getBankName()); e.setAccountNumber(s.getAccountNumber()); e.setIfsc(s.getIfsc());
                        repo.save(e);
                    }
                });
    }

    // ── Expenses ───────────────────────────────────────────────────────────────────────────────────────────────

    @Transactional
    public OfficeExpense raise(String actor, LocalDate date, String category, String description, String payee,
                              BigDecimal amount, String attachmentUrls, String bankName, String accountNumber, String ifsc) {
        AppUser a = manager(actor);
        if (date == null) date = LocalDate.now();
        if (category == null || category.isBlank()) throw new RuntimeException("Choose a category");
        if (amount == null || amount.signum() <= 0) throw new RuntimeException("Enter an amount greater than zero");
        return repo.save(OfficeExpense.builder().kind("EXPENSE").periodMonth(YearMonth.from(date).toString())
                .expenseDate(date).category(category.trim()).description(trim(description)).payee(trim(payee))
                .amount(amount).attachmentUrls(attachmentUrls).bankName(trim(bankName)).accountNumber(trim(accountNumber)).ifsc(upper(ifsc))
                .status("PENDING").raisedBy(a.getFullName()).build());
    }

    /** Edit a PENDING line. On a SALARY line only the amount and remarks can change. Null fields are unchanged. */
    @Transactional
    public OfficeExpense update(Long id, String actor, LocalDate date, String category, String description, String payee,
                               BigDecimal amount, String remarks, String attachmentUrls,
                               String bankName, String accountNumber, String ifsc) {
        manager(actor);
        OfficeExpense e = get(id);
        if (!"PENDING".equals(e.getStatus())) throw new RuntimeException("A paid line can't be changed — mark it unpaid first");
        if (amount != null) {
            if (amount.signum() < 0) throw new RuntimeException("Amount can't be negative");
            e.setAmount(amount);
        }
        if (remarks != null) e.setRemarks(trim(remarks));
        if ("EXPENSE".equals(e.getKind())) {
            if (date != null) { e.setExpenseDate(date); e.setPeriodMonth(YearMonth.from(date).toString()); }
            if (category != null && !category.isBlank()) e.setCategory(category.trim());
            if (description != null) e.setDescription(trim(description));
            if (payee != null) e.setPayee(trim(payee));
            if (attachmentUrls != null) e.setAttachmentUrls(attachmentUrls);
            if (bankName != null) e.setBankName(trim(bankName));
            if (accountNumber != null) e.setAccountNumber(trim(accountNumber));
            if (ifsc != null) e.setIfsc(upper(ifsc));
        }
        return repo.save(e);
    }

    @Transactional
    public void delete(Long id, String actor) {
        manager(actor);
        OfficeExpense e = get(id);
        if (!"PENDING".equals(e.getStatus())) throw new RuntimeException("A paid line can't be deleted");
        if ("SALARY".equals(e.getKind()) && e.getStaffId() != null
                && staffRepo.findById(e.getStaffId()).map(OfficeStaff::getActive).orElse(false))
            throw new RuntimeException("Monthly salary lines are created automatically — change the amount (e.g. to 0) instead, or deactivate the staff member");
        repo.delete(e);
    }

    // ── Payment (Accounts → Weekly Payments → Office/Mgnt) ─────────────────────────────────────────────────────

    @Transactional
    public OfficeExpense markPaid(Long id, String actor, LocalDate paidDate, String paymentRef) {
        AppUser a = manager(actor);
        OfficeExpense e = get(id);
        if ("PAID".equals(e.getStatus())) throw new RuntimeException("Already marked paid");
        e.setStatus("PAID");
        e.setPaidDate(paidDate == null ? LocalDate.now() : paidDate);
        e.setPaymentRef(trim(paymentRef));
        e.setPaidBy(a.getFullName());
        return repo.save(e);
    }

    /** Send for Payment: the pending lines put in the bank Excel are marked paid (sent to the bank) together. */
    @Transactional
    public List<OfficeExpense> sendForPayment(String actor, List<Long> ids) {
        AppUser a = manager(actor);
        if (ids == null || ids.isEmpty()) throw new RuntimeException("Nothing to send");
        LocalDate today = LocalDate.now();
        List<OfficeExpense> rows = repo.findAllById(ids);
        for (OfficeExpense e : rows) {
            if (!"PENDING".equals(e.getStatus())) continue;
            if (blank(e.getAccountNumber()) || blank(e.getIfsc())) throw new RuntimeException((e.getPayee() == null ? "A line" : e.getPayee()) + " has no account number / IFSC");
            e.setStatus("PAID"); e.setPaidDate(today); e.setPaidBy(a.getFullName()); e.setPaymentRef("Bank Excel " + today);
        }
        return repo.saveAll(rows);
    }

    @Transactional
    public OfficeExpense markUnpaid(Long id, String actor) {
        manager(actor);
        OfficeExpense e = get(id);
        e.setStatus("PENDING");
        e.setPaidDate(null);
        e.setPaymentRef(null);
        e.setPaidBy(null);
        return repo.save(e);
    }

    // ── Staff ──────────────────────────────────────────────────────────────────────────────────────────────────

    public List<OfficeStaff> staff(String actor) {
        viewer(actor);
        return staffRepo.findAllByOrderByIdAsc();
    }

    @Transactional
    public OfficeStaff addStaff(String actor, String name, String designation, BigDecimal amount, String startMonth,
                                String bankName, String accountNumber, String ifsc) {
        manager(actor);
        if (name == null || name.isBlank()) throw new RuntimeException("Enter the name");
        return staffRepo.save(OfficeStaff.builder().name(name.trim()).designation(trim(designation))
                .monthlyAmount(amount == null ? DEFAULT_SALARY : amount)
                .startMonth(startMonth == null || startMonth.isBlank() ? YearMonth.now().toString() : YearMonth.parse(startMonth).toString())
                .bankName(trim(bankName)).accountNumber(trim(accountNumber)).ifsc(upper(ifsc))
                .active(true).build());
    }

    /**
     * Change a staff member. A new monthly amount also updates their unpaid salary lines from this month on;
     * earlier months keep what was calculated for them.
     */
    @Transactional
    public OfficeStaff updateStaff(Long id, String actor, String name, String designation, BigDecimal amount, Boolean active,
                                   String bankName, String accountNumber, String ifsc) {
        manager(actor);
        OfficeStaff s = staffRepo.findById(id).orElseThrow(() -> new RuntimeException("Staff not found"));
        if (name != null && !name.isBlank()) s.setName(name.trim());
        if (designation != null) s.setDesignation(trim(designation));
        if (active != null) s.setActive(active);
        if (bankName != null) s.setBankName(trim(bankName));
        if (accountNumber != null) s.setAccountNumber(trim(accountNumber));
        if (ifsc != null) s.setIfsc(upper(ifsc));
        if (amount != null) {
            if (amount.signum() < 0) throw new RuntimeException("Amount can't be negative");
            s.setMonthlyAmount(amount);
            String from = YearMonth.now().toString();
            repo.findAll().stream()
                    .filter(e -> "SALARY".equals(e.getKind()) && id.equals(e.getStaffId()) && "PENDING".equals(e.getStatus())
                            && e.getPeriodMonth().compareTo(from) >= 0)
                    .forEach(e -> { e.setAmount(amount); repo.save(e); });
        }
        OfficeStaff saved = staffRepo.save(s);
        syncSalaryBank();
        return saved;
    }

    /** Removes a staff member and their unpaid salary lines. Not allowed once any of their lines is paid. */
    @Transactional
    public void deleteStaff(Long id, String actor) {
        manager(actor);
        OfficeStaff s = staffRepo.findById(id).orElseThrow(() -> new RuntimeException("Staff not found"));
        List<OfficeExpense> lines = repo.findAll().stream().filter(e -> "SALARY".equals(e.getKind()) && id.equals(e.getStaffId())).toList();
        if (lines.stream().anyMatch(e -> !"PENDING".equals(e.getStatus())))
            throw new RuntimeException(s.getName() + " has paid salary lines — stop them instead of deleting");
        repo.deleteAll(lines);
        staffRepo.delete(s);
    }

    // ── helpers ────────────────────────────────────────────────────────────────────────────────────────────────

    /** The staff member's Employee Master record: the stored link, or else the one whose name holds every word of theirs. */
    private Employee linkedEmployee(OfficeStaff s, List<Employee> master) {
        if (s.getEmployeeId() != null)
            return master.stream().filter(e -> s.getEmployeeId().equals(e.getId())).findFirst().orElse(null);
        Set<String> want = words(s.getName());
        List<Employee> hits = master.stream().filter(e -> e.getFullName() != null && words(e.getFullName()).containsAll(want)).toList();
        if (hits.size() != 1) return null; // none, or ambiguous — leave unlinked
        s.setEmployeeId(hits.get(0).getId());
        staffRepo.save(s);
        return hits.get(0);
    }

    private static Set<String> words(String n) {
        return java.util.Arrays.stream(n.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")).filter(w -> !w.isBlank())
                .collect(java.util.stream.Collectors.toSet());
    }

    private OfficeExpense get(Long id) {
        return repo.findById(id).orElseThrow(() -> new RuntimeException("Office expense not found"));
    }

    private static String label(YearMonth m) {
        return m.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + m.getYear();
    }

    private static String trim(String s) { return s == null ? null : s.trim(); }
    private static String upper(String s) { return s == null ? null : s.trim().toUpperCase(Locale.ROOT); }
    private static boolean blank(String s) { return s == null || s.isBlank(); }

    private AppUser user(String username) {
        return userRepo.findByUsernameAndActiveTrue(username == null ? "" : username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
    }

    private AppUser viewer(String username) {
        AppUser a = user(username);
        if (!VIEWERS.contains(a.getRole())) throw new RuntimeException("Not allowed");
        return a;
    }

    private AppUser manager(String username) {
        AppUser a = user(username);
        if (!MANAGERS.contains(a.getRole())) throw new RuntimeException("Only a VP or Admin can do this");
        return a;
    }
}

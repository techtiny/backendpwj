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

    private final OfficeExpenseRepository repo;
    private final OfficeStaffRepository staffRepo;
    private final AppUserRepository userRepo;
    private final EmployeeRepository employeeRepo;

    /** First run: the Office Mgnt salary staff, starting this month (each month's amount is entered on its line). */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedStaff() {
        // Lines from before the two divisions: salaries are Management, raised expenses Office.
        repo.findAll().stream().filter(e -> e.getDivision() == null).forEach(e -> {
            e.setDivision("SALARY".equals(e.getKind()) ? "MANAGEMENT" : "OFFICE");
            repo.save(e);
        });
        if (staffRepo.count() == 0) {
            String start = YearMonth.now().toString();
            for (String[] s : new String[][] { { "Anusha", "VP" }, { "Bharath", "OH" }, { "Sunil", "CEO" } }) {
                staffRepo.save(OfficeStaff.builder().name(s[0]).designation(s[1]).monthlyAmount(BigDecimal.ZERO)
                        .startMonth(start).active(true).build());
            }
        }
        applyDefaultSalaries();
    }

    /** Default monthly salary per person — prefilled on each month's line, still editable for that month. */
    private static final java.util.Map<String, BigDecimal> DEFAULT_SALARY = java.util.Map.of(
            "anusha", new BigDecimal("30000"), "bharath", new BigDecimal("100000"), "sunil", new BigDecimal("100000"));
    /** Amounts the earlier versions put on lines automatically (fixed ₹4,500, then blank). */
    private static final Set<BigDecimal> OLD_AUTO = Set.of(new BigDecimal("0.00"), new BigDecimal("4500.00"));

    /** Sets each person's default, and moves their unpaid lines still on an old automatic amount to it. */
    private void applyDefaultSalaries() {
        for (OfficeStaff s : staffRepo.findAll()) {
            BigDecimal d = DEFAULT_SALARY.get(s.getName().trim().toLowerCase(Locale.ROOT));
            if (d == null) continue;
            if (s.getMonthlyAmount() == null || s.getMonthlyAmount().compareTo(d) != 0) { s.setMonthlyAmount(d); staffRepo.save(s); }
            repo.findAll().stream()
                    .filter(e -> "SALARY".equals(e.getKind()) && s.getId().equals(e.getStaffId()) && "PENDING".equals(e.getStatus())
                            && e.getAmount() != null && OLD_AUTO.contains(e.getAmount().setScale(2, java.math.RoundingMode.HALF_UP)))
                    .forEach(e -> { e.setAmount(d); repo.save(e); });
        }
    }

    // ── Listing ────────────────────────────────────────────────────────────────────────────────────────────────

    /** month = YYYY-MM, or blank / ALL for every month; status = PENDING / PAID, or blank for both. */
    @Transactional
    public List<OfficeExpense> list(String actor, String month, String status) {
        return list(actor, month, status, null, false);
    }

    /** division = MANAGEMENT / OFFICE, or blank for both. */
    @Transactional
    public List<OfficeExpense> list(String actor, String month, String status, String division, boolean forPayments) {
        viewer(actor);
        ensureSalaries();
        boolean allMonths = month == null || month.isBlank() || "ALL".equalsIgnoreCase(month);
        List<OfficeExpense> rows = allMonths ? repo.findAllByOrderByPeriodMonthDescKindDescIdAsc()
                                             : repo.findByPeriodMonthOrderByKindDescIdAsc(YearMonth.parse(month).toString());
        if (status != null && !status.isBlank()) rows = rows.stream().filter(r -> status.equalsIgnoreCase(r.getStatus())).toList();
        if (division != null && !division.isBlank()) rows = rows.stream().filter(r -> division.equalsIgnoreCase(r.getDivision())).toList();
        if (forPayments) rows = rows.stream().filter(r -> Boolean.TRUE.equals(r.getSentForPayment())).toList();
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
                String prev = m.minusMonths(1).toString();
                boolean cf = repo.findByPeriodMonthOrderByKindDescIdAsc(prev).stream()
                        .anyMatch(e -> "SALARY".equals(e.getKind()) && s.getId().equals(e.getStaffId()) && Boolean.TRUE.equals(e.getInCashFlow()));
                repo.save(OfficeExpense.builder().kind("SALARY").division("MANAGEMENT").frequency("MONTHLY").inCashFlow(cf).periodMonth(m.toString()).category("Salary")
                        .description("Salary – " + label(m)).payee(s.getName())
                        .amount(s.getMonthlyAmount() == null ? BigDecimal.ZERO : s.getMonthlyAmount()) // default; editable per month
                        .staffId(s.getId()).bankName(s.getBankName()).accountNumber(s.getAccountNumber()).ifsc(s.getIfsc())
                        .status("PENDING").raisedBy("Auto (monthly)").build());
            }
        }
        syncSalaryBank();
        ensureRecurring();
    }

    /** Repeating expenses: a new line for every week / month / year since the original, up to today. */
    private void ensureRecurring() {
        LocalDate today = LocalDate.now();
        for (OfficeExpense src : repo.findByKindAndFrequencyIn("EXPENSE", List.of("WEEKLY", "MONTHLY", "YEARLY"))) {
            if (src.getRecurSourceId() != null || src.getExpenseDate() == null) continue; // only originals repeat
            for (LocalDate d = next(src.getExpenseDate(), src.getFrequency()); !d.isAfter(today); d = next(d, src.getFrequency())) {
                if (repo.existsByRecurSourceIdAndExpenseDate(src.getId(), d)) continue;
                repo.save(OfficeExpense.builder().kind("EXPENSE").division(src.getDivision()).frequency(src.getFrequency())
                        .recurSourceId(src.getId()).periodMonth(YearMonth.from(d).toString()).expenseDate(d)
                        .category(src.getCategory()).description(src.getDescription()).payee(src.getPayee()).amount(src.getAmount())
                        .bankName(src.getBankName()).accountNumber(src.getAccountNumber()).ifsc(src.getIfsc())
                        .inCashFlow(src.getInCashFlow()).status("PENDING").raisedBy("Auto (" + src.getFrequency().toLowerCase(Locale.ROOT) + ")").build());
            }
        }
    }

    private static LocalDate next(LocalDate d, String frequency) {
        return switch (frequency) { case "WEEKLY" -> d.plusWeeks(1); case "YEARLY" -> d.plusYears(1); default -> d.plusMonths(1); };
    }

    private static String frequency(String f) {
        if (f == null || f.isBlank()) return "ONE_TIME";
        String v = f.trim().toUpperCase(Locale.ROOT);
        if (!List.of("ONE_TIME", "WEEKLY", "MONTHLY", "YEARLY").contains(v)) throw new RuntimeException("Frequency must be One-time, Weekly, Monthly or Yearly");
        return v;
    }

    // ── Send for Payment (HR → Weekly Payments) and Cash Flow ─────────────────────────────────────────────────

    @Transactional
    public List<OfficeExpense> sendToPayments(String actor, List<Long> ids) {
        manager(actor);
        if (ids == null || ids.isEmpty()) throw new RuntimeException("Tick the lines to send");
        List<OfficeExpense> rows = repo.findAllById(ids);
        for (OfficeExpense e : rows) {
            if (e.getAmount() == null || e.getAmount().signum() <= 0) throw new RuntimeException((e.getPayee() == null ? "A line" : e.getPayee()) + " has no amount");
            if (!Boolean.TRUE.equals(e.getSentForPayment())) { e.setSentForPayment(true); e.setSentForPaymentAt(java.time.LocalDateTime.now()); }
        }
        return repo.saveAll(rows);
    }

    /** Back from Weekly Payments to HR — only while not yet paid. */
    @Transactional
    public List<OfficeExpense> recallFromPayments(String actor, List<Long> ids) {
        manager(actor);
        List<OfficeExpense> rows = repo.findAllById(ids == null ? List.of() : ids);
        for (OfficeExpense e : rows) {
            if ("PAID".equals(e.getStatus())) throw new RuntimeException("A paid line can't be taken back");
            e.setSentForPayment(false); e.setSentForPaymentAt(null);
        }
        return repo.saveAll(rows);
    }

    /** Put lines into (on = true) or take them out of Funds → Cash Flow → Planned Outflow. */
    @Transactional
    public List<OfficeExpense> setCashFlow(String actor, List<Long> ids, boolean on) {
        manager(actor);
        if (ids == null || ids.isEmpty()) throw new RuntimeException("Tick the lines first");
        List<OfficeExpense> rows = repo.findAllById(ids);
        rows.forEach(e -> e.setInCashFlow(on));
        // A repeated copy carries the flag back to its original so the coming repeats follow it.
        rows.stream().map(OfficeExpense::getRecurSourceId).filter(java.util.Objects::nonNull).distinct()
                .forEach(src -> repo.findById(src).ifPresent(o -> { o.setInCashFlow(on); repo.save(o); }));
        return repo.saveAll(rows);
    }

    /**
     * Planned outflow from Office/Mgnt: every line marked for Cash Flow, plus the upcoming repeats (next 12 months)
     * of repeating ones that don't exist yet. Each item: date (due), amount, payee, category, division, kind, projected.
     */
    @Transactional
    public List<java.util.Map<String, Object>> cashFlow(String actor) {
        viewer(actor);
        ensureSalaries();
        LocalDate today = LocalDate.now(), horizon = today.plusMonths(12);
        List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
        List<OfficeExpense> marked = repo.findAll().stream().filter(e -> Boolean.TRUE.equals(e.getInCashFlow())).toList();
        for (OfficeExpense e : marked) out.add(item(e, dueOf(e), false));
        // Upcoming repeats — expenses: from the latest line of each series; salaries: monthly per staff member.
        java.util.Map<String, OfficeExpense> latest = new java.util.HashMap<>();
        for (OfficeExpense e : marked) {
            String key = "SALARY".equals(e.getKind()) ? "S" + e.getStaffId()
                    : e.getFrequency() != null && !"ONE_TIME".equals(e.getFrequency()) ? "E" + (e.getRecurSourceId() != null ? e.getRecurSourceId() : e.getId()) : null;
            if (key == null) continue;
            OfficeExpense cur = latest.get(key);
            if (cur == null || dueOf(e).isAfter(dueOf(cur))) latest.put(key, e);
        }
        for (OfficeExpense e : latest.values()) {
            if ("SALARY".equals(e.getKind())) {
                OfficeStaff st = e.getStaffId() == null ? null : staffRepo.findById(e.getStaffId()).orElse(null);
                if (st == null || !Boolean.TRUE.equals(st.getActive())) continue;
                for (YearMonth m = YearMonth.parse(e.getPeriodMonth()).plusMonths(1); !m.atEndOfMonth().isAfter(horizon); m = m.plusMonths(1)) {
                    OfficeExpense p = OfficeExpense.builder().kind("SALARY").division("MANAGEMENT").periodMonth(m.toString())
                            .category("Salary").description("Salary – " + label(m)).payee(e.getPayee()).frequency("MONTHLY")
                            .amount(st.getMonthlyAmount() == null ? BigDecimal.ZERO : st.getMonthlyAmount()).build();
                    out.add(item(p, m.atEndOfMonth(), true));
                }
            } else {
                for (LocalDate d = next(e.getExpenseDate(), e.getFrequency()); !d.isAfter(horizon); d = next(d, e.getFrequency())) {
                    OfficeExpense p = OfficeExpense.builder().kind("EXPENSE").division(e.getDivision()).periodMonth(YearMonth.from(d).toString())
                            .expenseDate(d).category(e.getCategory()).description(e.getDescription()).payee(e.getPayee())
                            .frequency(e.getFrequency()).amount(e.getAmount()).build();
                    out.add(item(p, d, true));
                }
            }
        }
        return out;
    }

    /** Salaries fall due at the end of their month; expenses on their date. */
    private static LocalDate dueOf(OfficeExpense e) {
        if (e.getExpenseDate() != null) return e.getExpenseDate();
        return YearMonth.parse(e.getPeriodMonth()).atEndOfMonth();
    }

    private static java.util.Map<String, Object> item(OfficeExpense e, LocalDate due, boolean projected) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", e.getId()); m.put("kind", e.getKind()); m.put("division", e.getDivision());
        m.put("dueDate", due.toString()); m.put("amount", e.getAmount()); m.put("payee", e.getPayee());
        m.put("category", e.getCategory()); m.put("description", e.getDescription()); m.put("frequency", e.getFrequency());
        m.put("status", e.getStatus()); m.put("projected", projected);
        return m;
    }

    /** Unpaid salary lines follow the staff member's current bank details. */
    private void syncSalaryBank() {
        java.util.Map<Long, OfficeStaff> byId = new java.util.HashMap<>();
        staffRepo.findAll().forEach(s -> byId.put(s.getId(), s));
        repo.findByStatusOrderByPeriodMonthDescKindDescIdAsc("PENDING").stream()
                .peek(e -> { if ("EXPENSE".equals(e.getKind()) && e.getStaffId() == null) {
                        OfficeStaff p = staffNamed(e.getPayee());
                        if (p != null && !blank(p.getAccountNumber()) && !p.getAccountNumber().equals(e.getAccountNumber())) { e.withBankFrom(p); repo.save(e); }
                    } })
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
    public OfficeExpense raise(String actor, String division, String frequency, LocalDate date, String category, String description, String payee,
                              BigDecimal amount, String attachmentUrls, String bankName, String accountNumber, String ifsc) {
        AppUser a = manager(actor);
        if (date == null) date = LocalDate.now();
        if (category == null || category.isBlank()) throw new RuntimeException("Choose a category");
        if (amount == null || amount.signum() <= 0) throw new RuntimeException("Enter an amount greater than zero");
        return repo.save(OfficeExpense.builder().kind("EXPENSE").division(division(division)).frequency(frequency(frequency)).periodMonth(YearMonth.from(date).toString())
                .expenseDate(date).category(category.trim()).description(trim(description)).payee(trim(payee))
                .amount(amount).attachmentUrls(attachmentUrls).bankName(trim(bankName)).accountNumber(trim(accountNumber)).ifsc(upper(ifsc))
                .status("PENDING").raisedBy(a.getFullName()).build()
                .withBankFrom(staffNamed(payee)));
    }

    /** Edit a PENDING line. On a SALARY line only the amount and remarks can change. Null fields are unchanged. */
    @Transactional
    public OfficeExpense update(Long id, String actor, LocalDate date, String category, String description, String payee,
                               BigDecimal amount, String remarks, String attachmentUrls,
                               String bankName, String accountNumber, String ifsc, String frequency) {
        manager(actor);
        OfficeExpense e = get(id);
        // Every value can be edited, also on sent / paid lines (corrections).
        if (amount != null) {
            if (amount.signum() < 0) throw new RuntimeException("Amount can't be negative");
            e.setAmount(amount);
        }
        if (remarks != null) e.setRemarks(trim(remarks));
        if (description != null) e.setDescription(trim(description));
        if (attachmentUrls != null) e.setAttachmentUrls(attachmentUrls.isBlank() ? null : attachmentUrls);
        boolean salary = "SALARY".equals(e.getKind());
        if (date != null) {
            e.setExpenseDate(date);
            // A salary stays in its own month (one line per person per month); an expense moves with its date.
            if (!salary) e.setPeriodMonth(YearMonth.from(date).toString());
        }
        if (category != null && !category.isBlank()) e.setCategory(category.trim());
        if (payee != null && !payee.isBlank()) { e.setPayee(trim(payee)); e.withBankFrom(staffNamed(payee)); }
        if (frequency != null && !frequency.isBlank()) {
            String f = frequency(frequency);
            e.setFrequency(f);
            // Editing a repeat's frequency changes the whole series (its original).
            if (e.getRecurSourceId() != null) repo.findById(e.getRecurSourceId()).ifPresent(o -> { o.setFrequency(f); repo.save(o); });
        }
        if (!salary) {
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
            // Salaries go in the bank file and need bank details; expenses may be paid separately.
            if ("SALARY".equals(e.getKind()) && (blank(e.getAccountNumber()) || blank(e.getIfsc()))) throw new RuntimeException((e.getPayee() == null ? "A line" : e.getPayee()) + " has no account number / IFSC");
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
                .monthlyAmount(amount == null ? BigDecimal.ZERO : amount) // default for each month's line
                .startMonth(startMonth == null || startMonth.isBlank() ? YearMonth.now().toString() : YearMonth.parse(startMonth).toString())
                .bankName(trim(bankName)).accountNumber(trim(accountNumber)).ifsc(upper(ifsc))
                .active(true).build());
    }

    /**
     * Change a staff member. There is no fixed monthly amount — each month's salary is entered on its own line.
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

    /** The Office Mgnt person an expense is paid to (by name), or null. */
    private OfficeStaff staffNamed(String payee) {
        if (blank(payee)) return null;
        return staffRepo.findAllByOrderByIdAsc().stream().filter(x -> x.getName().equalsIgnoreCase(payee.trim())).findFirst().orElse(null);
    }

    private static String division(String d) {
        if (d == null || d.isBlank()) return "OFFICE";
        String v = d.trim().toUpperCase(Locale.ROOT);
        if (!v.equals("MANAGEMENT") && !v.equals("OFFICE")) throw new RuntimeException("Division must be Management or Office");
        return v;
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

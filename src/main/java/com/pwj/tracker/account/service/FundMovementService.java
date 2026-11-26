package com.pwj.tracker.account.service;

import com.pwj.tracker.account.dto.FundMovementDto;
import com.pwj.tracker.account.entity.ExpenseItem;
import com.pwj.tracker.account.entity.FundMovement;
import com.pwj.tracker.account.repository.ExpenseItemRepository;
import com.pwj.tracker.account.repository.FundMovementRepository;
import com.pwj.tracker.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class FundMovementService {

    private final FundMovementRepository repo;
    private final ProjectRepository projectRepo;
    private final ExpenseItemRepository expenseRepo;

    public FundMovementService(FundMovementRepository repo, ProjectRepository projectRepo,
                                ExpenseItemRepository expenseRepo) {
        this.repo = repo;
        this.projectRepo = projectRepo;
        this.expenseRepo = expenseRepo;
    }

    public List<FundMovementDto> list(String direction) {
        Map<Long, String> names = new HashMap<>();
        projectRepo.findAll().forEach(p -> names.put(p.getId(), p.getName()));
        List<FundMovement> rows = (direction == null || direction.isBlank())
                ? repo.findAllByOrderByMovementDateDescIdDesc()
                : repo.findByDirectionOrderByMovementDateDescIdDesc(direction.trim().toUpperCase());
        return rows.stream().map(m -> toDto(m, names)).toList();
    }

    public FundMovementDto create(FundMovementDto dto) {
        String dir = dto.getDirection() == null ? "" : dto.getDirection().trim().toUpperCase();
        if (!"INFLOW".equals(dir) && !"OUTFLOW".equals(dir)) {
            throw new IllegalStateException("direction must be INFLOW or OUTFLOW");
        }
        BigDecimal amount = dto.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Amount must be greater than zero");
        }

        String party = dto.getParty() == null ? "" : dto.getParty().trim();
        Long projectId = dto.getProjectId();
        if (projectId != null) {
            String name = projectRepo.findById(projectId).map(com.pwj.tracker.model.Project::getName).orElse(null);
            if (name == null) throw new IllegalArgumentException("Project not found: " + projectId);
            if (party.isBlank()) party = name;
        }
        if (party.isBlank()) {
            throw new IllegalStateException("OUTFLOW".equals(dir)
                    ? "Select the project the payment was made to"
                    : "Enter a Source Type");
        }

        FundMovement m = new FundMovement();
        m.setDirection(dir);
        m.setMovementDate(dto.getMovementDate() != null ? dto.getMovementDate() : LocalDate.now());
        m.setParty(party);
        m.setProjectId(projectId);
        m.setAmount(amount);
        m.setMode(dto.getMode() == null || dto.getMode().isBlank() ? null : dto.getMode().trim());
        m.setRemarks(dto.getRemarks() == null || dto.getRemarks().isBlank() ? null : dto.getRemarks().trim());

        Map<Long, String> names = new HashMap<>();
        projectRepo.findAll().forEach(p -> names.put(p.getId(), p.getName()));
        return toDto(repo.save(m), names);
    }

    public void delete(Long id) {
        FundMovement m = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Fund movement not found: " + id));
        if (m.getSourceRef() != null && m.getSourceRef().startsWith("SALARY-"))
            throw new IllegalStateException("This entry was created automatically from the approved salary payment and can't be deleted here");
        repo.deleteById(id);
    }

    /** Removes the Actual Outflow created for a month's approved salary payment — used when the VP revises (reopens)
     *  that month. System-initiated, so it bypasses the "can't delete by hand" rule. Returns the removed amount, or
     *  null if the month had no entry. */
    @Transactional
    public BigDecimal removeSalaryOutflow(int year, int month) {
        return repo.findBySourceRef(String.format("SALARY-%d-%02d", year, month)).map(m -> {
            repo.delete(m);
            return m.getAmount();
        }).orElse(null);
    }

    /** Records the approved salary payment of a month as an Actual Outflow — not tied to any project, so project
     *  balances and Payment Funding are unaffected. Idempotent: one entry per month. Joins the caller's transaction,
     *  so if this fails the salary approval rolls back with it. */
    @Transactional
    public void recordSalaryOutflow(int year, int month, BigDecimal amount, String remarks) {
        String ref = String.format("SALARY-%d-%02d", year, month);
        if (repo.existsBySourceRef(ref)) return;
        if (amount == null || amount.signum() <= 0) return;
        FundMovement m = new FundMovement();
        m.setDirection("OUTFLOW");
        m.setMovementDate(LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")));
        m.setParty("Salary — " + java.time.Month.of(month).getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH) + " " + year);
        m.setProjectId(null);
        m.setAmount(amount);
        m.setMode("Bank Transfer / NEFT");
        m.setRemarks(remarks);
        m.setSourceRef(ref);
        repo.save(m);
    }

    private BigDecimal safe(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    /** projectId -> [inflow, outflow] from all fund movements. */
    private Map<Long, BigDecimal[]> aggByProject() {
        Map<Long, BigDecimal[]> agg = new LinkedHashMap<>();
        for (Object[] row : repo.sumGroupedByProjectAndDirection()) {
            Long pid = ((Number) row[0]).longValue();
            String dir = String.valueOf(row[1]);
            BigDecimal sum = (BigDecimal) row[2];
            BigDecimal[] a = agg.computeIfAbsent(pid, k -> new BigDecimal[]{ BigDecimal.ZERO, BigDecimal.ZERO });
            if ("INFLOW".equals(dir)) a[0] = a[0].add(sum); else a[1] = a[1].add(sum);
        }
        return agg;
    }

    /** Per-project fund balance: total inflow received for the project minus total outflow paid to it. */
    public List<Map<String, Object>> balances() {
        Map<Long, String> names = new HashMap<>();
        projectRepo.findAll().forEach(p -> names.put(p.getId(), p.getName()));

        List<Map<String, Object>> out = new ArrayList<>();
        aggByProject().forEach((pid, a) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("projectId", pid);
            m.put("projectName", names.getOrDefault(pid, "Unknown"));
            m.put("inflow", a[0]);
            m.put("outflow", a[1]);
            m.put("available", a[0].subtract(a[1]));
            out.add(m);
        });
        out.sort((x, y) -> String.valueOf(x.get("projectName")).compareToIgnoreCase(String.valueOf(y.get("projectName"))));
        return out;
    }

    /** Today's bank-transfer demand per project: VP + CEO approved Send-for-Payment items in today's bank file. */
    private Map<Long, BigDecimal> todaysDemand(LocalDate today) {
        Map<Long, BigDecimal> demand = new LinkedHashMap<>();
        for (ExpenseItem e : expenseRepo.findSentForPayment()) {
            String vp = e.getVpApprovalStatus() == null ? "" : e.getVpApprovalStatus().trim().toUpperCase();
            if (!"APPROVED".equals(vp)) continue;
            // Level 4: only CEO-approved entries are part of the bank transfer.
            if (!"APPROVED".equals(ExpenseItemService.effectiveCeoStatus(e))) continue;
            // An entry rejected at any level never counts towards today's bank-transfer demand.
            if ("REJECTED".equalsIgnoreCase(e.getOhApprovalStatus()) || "REJECTED".equalsIgnoreCase(e.getAdminApprovalStatus())) continue;
            if (!today.equals(ExpenseItemService.bankFileDay(e))) continue; // sent today, unless its Required By pushes it to a later cycle
            if (e.getProjectId() == null) continue;
            BigDecimal pay = e.getApprovedValue() != null ? e.getApprovedValue() : safe(e.getSentAmount());
            demand.merge(e.getProjectId(), pay, BigDecimal::add);
        }

        return demand;
    }

    /**
     * Compares each project's available fund against today's bank-transfer demand (VP-approved
     * Send-for-Payment items sent today). Lists shortfalls and the surplus projects that can
     * cover them; the bank file is "eligible" only once every project is funded.
     */
    public Map<String, Object> paymentFunding() {
        LocalDate today = LocalDate.now();
        Map<Long, String> names = new HashMap<>();
        projectRepo.findAll().forEach(p -> names.put(p.getId(), p.getName()));

        Map<Long, BigDecimal> demand = todaysDemand(today);

        Map<Long, BigDecimal[]> agg = aggByProject();
        java.util.function.Function<Long, BigDecimal> availOf =
                pid -> { BigDecimal[] a = agg.get(pid); return a == null ? BigDecimal.ZERO : a[0].subtract(a[1]); };

        List<Map<String, Object>> projects = new ArrayList<>();
        BigDecimal totalShortfall = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal> en : demand.entrySet()) {
            Long pid = en.getKey();
            BigDecimal dem = en.getValue();
            BigDecimal avail = availOf.apply(pid);
            BigDecimal shortfall = dem.subtract(avail).max(BigDecimal.ZERO);
            totalShortfall = totalShortfall.add(shortfall);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("projectId", pid);
            m.put("projectName", names.getOrDefault(pid, "Unknown"));
            m.put("available", avail);
            m.put("demand", dem);
            m.put("shortfall", shortfall);
            m.put("funded", shortfall.signum() == 0);
            projects.add(m);
        }
        projects.sort(Comparator.comparing(x -> (boolean) x.get("funded"))); // unfunded first

        // Surplus = available beyond that project's own demand and what it still owes other projects
        List<Debt> allDebts = outstandingDebts();
        List<Map<String, Object>> surplus = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal[]> en : agg.entrySet()) {
            Long pid = en.getKey();
            BigDecimal avail = en.getValue()[0].subtract(en.getValue()[1]);
            BigDecimal ownDemand = demand.getOrDefault(pid, BigDecimal.ZERO);
            BigDecimal beyondDemand = avail.subtract(ownDemand);
            if (beyondDemand.signum() > 0) {
                List<Map<String, Object>> owes = allDebts.stream().filter(d -> d.borrowerId().equals(pid)).map(this::debtMap).toList();
                BigDecimal owed = owes.stream().map(o -> (BigDecimal) o.get("outstanding")).reduce(BigDecimal.ZERO, BigDecimal::add);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("projectId", pid);
                m.put("projectName", names.getOrDefault(pid, "Unknown"));
                m.put("available", avail);
                m.put("owed", owed);
                m.put("debts", owes);
                m.put("free", beyondDemand.subtract(owed).max(BigDecimal.ZERO));
                surplus.add(m);
            }
        }
        surplus.sort((a, b) -> ((BigDecimal) b.get("free")).compareTo((BigDecimal) a.get("free")));

        boolean allFunded = projects.stream().allMatch(p -> (boolean) p.get("funded"));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("date", today.toString());
        out.put("fundingInUse", repo.count() > 0);
        out.put("projects", projects);
        out.put("surplus", surplus);
        out.put("totalShortfall", totalShortfall);
        out.put("allFunded", allFunded);
        out.put("loans", loans());
        return out;
    }

    // ─── Inter-project loans ───────────────────────────────────────────────────────────────────────────────
    // Every Fund Transfer between projects is a loan: if F sends ₹5L to A, A owes F ₹5L until A sends it back.
    // Debts are netted per pair (A→F transfers reduce what A owes F) and repaid oldest lender first.

    static final String MODE_TRANSFER = "Fund Transfer";
    static final String REF_LOAN = "LOAN";
    static final String REF_REPAY = "LOAN-REPAY";

    /** One outstanding debt: borrower owes lender `outstanding`. */
    public record Debt(Long borrowerId, String borrowerName, Long lenderId, String lenderName,
                       BigDecimal outstanding, LocalDate since) {}

    /** All outstanding inter-project debts, oldest loan first. */
    public List<Debt> outstandingDebts() {
        return debts(false);
    }

    /**
     * Inter-project debts, oldest loan first. With includeSettled, pairs that have been fully repaid are kept too
     * (outstanding = 0), so the loan history stays visible after settlement. A settled pair is listed once, in the
     * direction of its first transfer (the original loan).
     */
    private List<Debt> debts(boolean includeSettled) {
        Map<Long, String> names = new HashMap<>();
        Map<String, Long> idsByName = new HashMap<>();
        projectRepo.findAll().forEach(p -> { names.put(p.getId(), p.getName()); idsByName.putIfAbsent(p.getName(), p.getId()); });

        // lent[lender][borrower] = total sent; first[lender][borrower] = date of first transfer
        Map<Long, Map<Long, BigDecimal>> lent = new HashMap<>();
        Map<Long, Map<Long, LocalDate>> first = new HashMap<>();
        Map<Long, Map<Long, Long>> firstId = new HashMap<>(); // id of the first transfer, to order same-day transfers
        for (FundMovement m : repo.findByModeAndDirectionOrderByMovementDateAscIdAsc(MODE_TRANSFER, "OUTFLOW")) {
            Long lender = m.getProjectId();
            Long borrower = m.getCounterpartyProjectId() != null ? m.getCounterpartyProjectId() : idsByName.get(m.getParty());
            if (lender == null || borrower == null || lender.equals(borrower)) continue;
            lent.computeIfAbsent(lender, k -> new HashMap<>()).merge(borrower, safe(m.getAmount()), BigDecimal::add);
            first.computeIfAbsent(lender, k -> new HashMap<>()).putIfAbsent(borrower, m.getMovementDate());
            firstId.computeIfAbsent(lender, k -> new HashMap<>()).putIfAbsent(borrower, m.getId());
        }

        List<Debt> debts = new ArrayList<>();
        lent.forEach((lender, byBorrower) -> byBorrower.forEach((borrower, amt) -> {
            BigDecimal back = lent.getOrDefault(borrower, Map.of()).getOrDefault(lender, BigDecimal.ZERO);
            BigDecimal net = amt.subtract(back);
            boolean settledOriginal = includeSettled && net.signum() == 0
                    && startedFirst(first, firstId, lender, borrower);
            if (net.signum() > 0 || settledOriginal) {
                debts.add(new Debt(borrower, names.getOrDefault(borrower, "Unknown"), lender, names.getOrDefault(lender, "Unknown"),
                        net, first.get(lender).get(borrower)));
            }
        }));
        debts.sort(Comparator.comparing(Debt::since, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(Debt::lenderName));
        return debts;
    }

    /** True when lender → borrower transfers began before any borrower → lender transfer (i.e. it was the original loan). */
    private static boolean startedFirst(Map<Long, Map<Long, LocalDate>> first, Map<Long, Map<Long, Long>> firstId, Long lender, Long borrower) {
        LocalDate there = first.get(lender).get(borrower);
        LocalDate back = first.getOrDefault(borrower, Map.of()).get(lender);
        if (back == null) return true;
        if (there == null) return false;
        if (!there.equals(back)) return there.isBefore(back);
        Long a = firstId.get(lender).get(borrower), b = firstId.getOrDefault(borrower, Map.of()).get(lender);
        return a == null || b == null || a < b;
    }

    /** What one project still owes, oldest lender first. */
    public List<Debt> debtsOf(Long borrowerId) {
        return outstandingDebts().stream().filter(d -> d.borrowerId().equals(borrowerId)).toList();
    }

    public List<Map<String, Object>> loans() {
        Map<Long, BigDecimal[]> agg = aggByProject();
        Map<Long, BigDecimal> demand = todaysDemand(LocalDate.now());
        List<Map<String, Object>> out = new ArrayList<>();
        for (Debt d : debts(true)) { // settled loans included so their history stays listed
            BigDecimal[] a = agg.get(d.borrowerId());
            BigDecimal avail = a == null ? BigDecimal.ZERO : a[0].subtract(a[1]);
            Map<String, Object> m = debtMap(d);
            m.put("borrowerAvailable", avail);
            m.put("borrowerSpare", avail.subtract(demand.getOrDefault(d.borrowerId(), BigDecimal.ZERO)).max(BigDecimal.ZERO));
            m.put("settled", d.outstanding().signum() == 0);
            out.add(m);
        }
        return out;
    }

    private Map<String, Object> debtMap(Debt d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("borrowerId", d.borrowerId());
        m.put("borrowerName", d.borrowerName());
        m.put("lenderId", d.lenderId());
        m.put("lenderName", d.lenderName());
        m.put("outstanding", d.outstanding());
        m.put("since", d.since());
        return m;
    }

    private BigDecimal availableOf(Long projectId) {
        BigDecimal[] a = aggByProject().get(projectId);
        return a == null ? BigDecimal.ZERO : a[0].subtract(a[1]);
    }

    private String nameOf(Long id, String role) {
        return projectRepo.findById(id).map(com.pwj.tracker.model.Project::getName)
                .orElseThrow(() -> new IllegalArgumentException(role + " project not found"));
    }

    private static String inr(BigDecimal v) {
        // Indian grouping: ₹12,34,567.00
        String plain = (v == null ? BigDecimal.ZERO : v).setScale(2, java.math.RoundingMode.HALF_UP).abs().toPlainString();
        String whole = plain.substring(0, plain.indexOf('.')), paise = plain.substring(plain.indexOf('.'));
        if (whole.length() > 3) {
            String head = whole.substring(0, whole.length() - 3), tail = whole.substring(whole.length() - 3);
            head = head.replaceAll("\\B(?=(\\d{2})+$)", ",");
            whole = head + "," + tail;
        }
        return (v != null && v.signum() < 0 ? "-₹" : "₹") + whole + paise;
    }

    /** Saves the paired OUTFLOW (from) / INFLOW (to) legs of one transfer. */
    private void saveTransferLegs(Long fromId, String fromName, Long toId, String toName,
                                  BigDecimal amount, String remarks, String ref) {
        LocalDate today = LocalDate.now();
        FundMovement out = new FundMovement();
        out.setDirection("OUTFLOW"); out.setMovementDate(today); out.setProjectId(fromId); out.setCounterpartyProjectId(toId);
        out.setParty(toName); out.setAmount(amount); out.setMode(MODE_TRANSFER); out.setSourceRef(ref);
        out.setRemarks(remarks != null ? remarks : "Fund transfer to " + toName);

        FundMovement in = new FundMovement();
        in.setDirection("INFLOW"); in.setMovementDate(today); in.setProjectId(toId); in.setCounterpartyProjectId(fromId);
        in.setParty(fromName); in.setAmount(amount); in.setMode(MODE_TRANSFER); in.setSourceRef(ref);
        in.setRemarks(remarks != null ? remarks : "Fund transfer from " + fromName);

        repo.save(out);
        repo.save(in);
    }

    /**
     * Move fund from one project to another. If the source project borrowed from other projects earlier, those
     * debts are repaid to the lenders first (oldest first) and only then is the new transfer sent — so the source
     * needs enough for both. Sending money back to a lender is itself recorded as a repayment.
     * Returns what happened: the repayments made and the transfer.
     */
    @Transactional
    public Map<String, Object> transferFund(Long fromProjectId, Long toProjectId, BigDecimal amount, String remarks) {
        if (fromProjectId == null || toProjectId == null) throw new IllegalArgumentException("Both projects are required");
        if (fromProjectId.equals(toProjectId)) throw new IllegalStateException("Source and destination cannot be the same");
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalStateException("Amount must be greater than zero");

        String fromName = nameOf(fromProjectId, "Source");
        String toName = nameOf(toProjectId, "Destination");
        String note = remarks == null || remarks.isBlank() ? null : remarks.trim();

        BigDecimal fromAvailable = availableOf(fromProjectId);
        BigDecimal ownDemand = todaysDemand(LocalDate.now()).getOrDefault(fromProjectId, BigDecimal.ZERO);
        BigDecimal usable = fromAvailable.subtract(ownDemand); // its own bills due today come first
        List<Debt> debts = debtsOf(fromProjectId);
        // Money going back to a lender is a repayment, not a new loan — that lender's debt isn't settled separately.
        Debt toLender = debts.stream().filter(d -> d.lenderId().equals(toProjectId)).findFirst().orElse(null);
        List<Debt> repayFirst = debts.stream().filter(d -> !d.lenderId().equals(toProjectId)).toList();
        BigDecimal owed = repayFirst.stream().map(Debt::outstanding).reduce(BigDecimal.ZERO, BigDecimal::add);

        if (owed.add(amount).compareTo(usable) > 0) {
            String demandNote = ownDemand.signum() > 0 ? " (" + inr(ownDemand) + " is kept for its own bills due today)" : "";
            BigDecimal free = usable.subtract(owed).max(BigDecimal.ZERO);
            if (owed.signum() > 0) {
                StringBuilder who = new StringBuilder();
                for (Debt d : repayFirst) who.append(who.length() > 0 ? ", " : "").append(inr(d.outstanding())).append(" to ").append(d.lenderName());
                throw new IllegalStateException(fromName + " has already borrowed fund and must repay it first (" + who
                        + "). After repaying, only " + inr(free) + " is free to transfer" + demandNote + ".");
            }
            throw new IllegalStateException(fromName + " has only " + inr(free) + " free to transfer" + demandNote + ".");
        }

        List<Map<String, Object>> repaid = new ArrayList<>();
        for (Debt d : repayFirst) {
            saveTransferLegs(fromProjectId, fromName, d.lenderId(), d.lenderName(), d.outstanding(),
                    "Repayment of fund borrowed from " + d.lenderName() + " (auto, before transfer to " + toName + ")", REF_REPAY);
            repaid.add(debtMap(d));
        }

        String ref = toLender != null ? REF_REPAY : REF_LOAN;
        if (note == null && toLender != null) note = "Repayment of fund borrowed from " + toName;
        saveTransferLegs(fromProjectId, fromName, toProjectId, toName, amount, note, ref);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("repaid", repaid);
        result.put("transferred", amount);
        result.put("from", fromName);
        result.put("to", toName);
        result.put("isRepayment", toLender != null);
        return result;
    }

    /** Borrower pays back a lender — the outstanding amount, or `amount` if given, capped at what the borrower has. */
    @Transactional
    public Map<String, Object> repay(Long borrowerId, Long lenderId, BigDecimal amount) {
        if (borrowerId == null || lenderId == null) throw new IllegalArgumentException("Borrower and lender are required");
        Debt d = debtsOf(borrowerId).stream().filter(x -> x.lenderId().equals(lenderId)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Nothing is owed between these projects"));
        BigDecimal ownDemand = todaysDemand(LocalDate.now()).getOrDefault(borrowerId, BigDecimal.ZERO);
        BigDecimal available = availableOf(borrowerId).subtract(ownDemand); // its own bills due today come first
        BigDecimal pay = amount != null && amount.signum() > 0 ? amount.min(d.outstanding()) : d.outstanding();
        if (available.signum() <= 0)
            throw new IllegalStateException(d.borrowerName() + " has no spare fund to repay " + d.lenderName()
                    + (ownDemand.signum() > 0 ? " — its " + inr(ownDemand) + " is needed for its own bills due today" : ""));
        pay = pay.min(available);
        saveTransferLegs(borrowerId, d.borrowerName(), lenderId, d.lenderName(), pay,
                "Repayment of fund borrowed from " + d.lenderName(), REF_REPAY);
        Map<String, Object> m = debtMap(d);
        m.put("repaid", pay);
        m.put("remaining", d.outstanding().subtract(pay));
        return m;
    }

    private FundMovementDto toDto(FundMovement m, Map<Long, String> names) {
        FundMovementDto d = new FundMovementDto();
        d.setId(m.getId());
        d.setDirection(m.getDirection());
        d.setMovementDate(m.getMovementDate());
        d.setParty(m.getParty());
        d.setProjectId(m.getProjectId());
        d.setProjectName(m.getProjectId() != null ? names.get(m.getProjectId()) : null);
        d.setAmount(m.getAmount());
        d.setMode(m.getMode());
        d.setRemarks(m.getRemarks());
        d.setSourceRef(m.getSourceRef());
        d.setCreatedAt(m.getCreatedAt());
        return d;
    }
}

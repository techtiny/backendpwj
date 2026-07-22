package com.pwj.tracker.service;

import com.pwj.tracker.dto.AssistantChatRequest;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * In-app help assistant for the PWJ Tracker. This is a deterministic FAQ
 * matcher — no external AI API / API key required. It scores the user's
 * question against a small hand-written knowledge base and returns the
 * best-matching answer as an intro line plus an ordered flow of steps, so
 * the frontend can render it as a step flow instead of a wall of text.
 */
@Service
public class AssistantService {

    private static final Pattern WORD_SPLIT = Pattern.compile("[^a-z0-9]+");
    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "the", "is", "are", "do", "does", "how", "what", "where", "when",
            "why", "to", "for", "in", "on", "of", "i", "my", "me", "can", "should", "it",
            "this", "that", "and", "or", "with", "up", "get", "gets", "please"
    );
    private static final String[] NO_PHRASES = new String[0];

    private final List<Entry> knowledgeBase = buildKnowledgeBase();

    public ChatAnswer chat(AssistantChatRequest req) {
        if (req.getMessages() == null || req.getMessages().isEmpty()) {
            throw new RuntimeException("No message provided");
        }
        String question = lastUserMessage(req.getMessages());
        if (question == null || question.isBlank()) {
            throw new RuntimeException("No message provided");
        }

        Set<String> queryTokens = tokenize(question);
        String questionLower = question.toLowerCase(Locale.ROOT);
        Entry best = null;
        int bestScore = 0;
        for (Entry e : knowledgeBase) {
            int score = 0;
            for (String kw : e.keywords) {
                if (queryTokens.contains(kw)) score += 2;
                else if (questionLower.contains(kw)) score += 1;
            }
            // Phrases are exact, higher-signal substrings — worth more than a single
            // keyword overlap, so a specific scenario entry wins over a broader one
            // that happens to share a generic keyword (e.g. "vendor", "document").
            for (String phrase : e.phrases) {
                if (questionLower.contains(phrase)) score += 5;
            }
            if (score > bestScore) { bestScore = score; best = e; }
        }

        if (best == null || bestScore < 2) {
            return fallback();
        }
        return new ChatAnswer(best.intro, Arrays.asList(best.steps), best.note);
    }

    private String lastUserMessage(List<AssistantChatRequest.Turn> turns) {
        for (int i = turns.size() - 1; i >= 0; i--) {
            if ("user".equals(turns.get(i).getRole())) return turns.get(i).getContent();
        }
        return null;
    }

    private Set<String> tokenize(String text) {
        Set<String> out = new HashSet<>();
        for (String w : WORD_SPLIT.split(text.toLowerCase(Locale.ROOT))) {
            if (!w.isBlank() && !STOPWORDS.contains(w)) out.add(w);
        }
        return out;
    }

    private ChatAnswer fallback() {
        return new ChatAnswer(
                "I'm not sure about that one yet. Here's what I can help with — try rephrasing, or pick a topic:",
                List.of(
                        "Raising a PR (Purchase/Work/Job Requirement)",
                        "OH approval and what \"Dependency\" means",
                        "Assigning a vendor and generating a PO / WO / JO",
                        "VP document approval, revisions, and rejections",
                        "Clubbing/splitting items across vendors, or partially issued documents",
                        "Sending a PR back to Site Team for clarification",
                        "Reporting a bug",
                        "Attendance check-in / check-out",
                        "Leave requests and approvals",
                        "Petty cash / reimbursement"
                ),
                "If this is about something looking wrong with your actual data, please report it via the Bug Tracker instead — I can't see live records."
        );
    }

    /** API response shape: a one-line intro, an ordered flow of steps, and an optional trailing note. */
    public record ChatAnswer(String intro, List<String> steps, String note) {}

    private record Entry(String[] keywords, String[] phrases, String intro, String[] steps, String note) {}

    private List<Entry> buildKnowledgeBase() {
        List<Entry> kb = new ArrayList<>();

        kb.add(new Entry(
                new String[]{"raise", "create", "new", "pr", "requirement", "request"}, NO_PHRASES,
                "To raise a new PR (Purchase/Work/Job Requirement):",
                new String[]{
                        "Go to the main Entries tab",
                        "Click \"+ New PR\"",
                        "Fill in project, BOQ number, material/work required, specification, unit, quantity, and date of requirement",
                        "Submit — it starts as approvalStatus = Hold, dependency = \"OH Approval\"",
                        "You can still edit it until OH approves — it locks after that"
                }, null));

        kb.add(new Entry(
                new String[]{"dependency", "stage", "status", "mean", "column"}, NO_PHRASES,
                "The \"Dependency\" column shows which team currently owns the next action on a PR — it flows through:",
                new String[]{
                        "OH Approval",
                        "Procurement",
                        "Site team — only if Procurement asks for clarification",
                        "Vendor",
                        "DIP — dispatch/delivery in progress, set once VP approves the document"
                },
                "A small speech-bubble icon next to it means there are remarks worth opening the entry for."));

        kb.add(new Entry(
                new String[]{"oh", "approve", "approval", "proceed", "hold", "reject", "notapproved"}, NO_PHRASES,
                "OH reviews every new PR in the \"Pending OH Approval\" queue:",
                new String[]{
                        "Open the Pending OH Approval queue",
                        "Pick Proceed, Hold, or Not Approved",
                        "Proceed → submits immediately, dependency moves to \"Procurement\"",
                        "Hold / Not Approved → a written remark is required, and it's sent back to Site Team to revise"
                }, null));

        kb.add(new Entry(
                new String[]{"vendor", "assign", "po", "wo", "jo", "generate", "document", "order"}, NO_PHRASES,
                "Once OH approves a PR, Procurement generates the document:",
                new String[]{
                        "Assign a vendor to the PR",
                        "Choose PO (Purchase Order), WO (Work Order), or JO (Job Order)",
                        "Fill every item row's Unit, Qty, and Rate — required before saving or submitting",
                        "Optionally club several items for the same vendor into one document, or split across multiple vendors",
                        "Submit for VP approval"
                }, null));

        kb.add(new Entry(
                new String[]{"vp", "verify", "issue", "issued", "signature", "pdf", "print", "download"}, NO_PHRASES,
                "After Procurement submits a PO/WO/JO, it goes to the VP:",
                new String[]{
                        "VP reviews it in the \"Document Approvals\" queue",
                        "Approve, mark Not Approved, or request a revision",
                        "Revision → sent back to Procurement with comments",
                        "Approved → dependency moves to \"DIP\", document is \"Issued\"",
                        "The PDF (with signature blocks) can then be downloaded or emailed to the vendor"
                }, null));

        kb.add(new Entry(
                new String[]{"club", "combine", "merge", "split", "multiple", "vendors"}, NO_PHRASES,
                "Combining or splitting a PR across vendors:",
                new String[]{
                        "Clubbing — combine multiple line items for the same vendor into one PO/WO/JO",
                        "Splitting — divide a PR across several vendors instead",
                        "Each vendor's portion is then tracked and approved independently"
                }, null));

        kb.add(new Entry(
                new String[]{"clarify", "clarification", "site", "team", "question", "remark", "comment"}, NO_PHRASES,
                "Sending a PR back to Site Team for clarification:",
                new String[]{
                        "Procurement opens the PR's detail view",
                        "Sends a clarification request — dependency moves to \"Site team\"",
                        "OH's original approval decision is not undone",
                        "Site Team replies from the same detail view",
                        "Dependency moves back to \"Procurement\" automatically"
                },
                "Look for the speech-bubble icon in the Dependency column to spot PRs with remarks."));

        kb.add(new Entry(
                new String[]{"bug", "report", "issue", "error", "broken", "screenshot", "wrong"}, NO_PHRASES,
                "Reporting a bug:",
                new String[]{
                        "Open the Bug Tracker tab",
                        "Click \"+ Report Bug\"",
                        "Fill in Title, Description, Module, and Severity",
                        "Attach a screenshot — click to browse, or paste with Ctrl+V / Cmd+V",
                        "Track it through Open → In Progress → Testing → Resolved → Closed, and the comment log"
                }, null));

        kb.add(new Entry(
                new String[]{"attendance", "checkin", "checkout", "check", "location", "gps"}, NO_PHRASES,
                "Check-in / check-out:",
                new String[]{
                        "Go to HR → Attendance",
                        "Check in / check out — GPS location is captured automatically",
                        "Admins/OH/VP/CEO see everyone under HR → All Attendance",
                        "\"Needs Review — Missing Check-In/Out\" tab shows records auto-marked Absent — correct them right there"
                }, null));

        kb.add(new Entry(
                new String[]{"leave", "permission", "vacation", "absent", "sick"}, NO_PHRASES,
                "Leave and permission requests:",
                new String[]{
                        "Apply under HR → My Leaves",
                        "Approvers (VP, OH, Admin, CEO) act on it under HR → Leave Approvals"
                }, null));

        kb.add(new Entry(
                new String[]{"petty", "cash", "reimbursement", "expense", "claim"}, NO_PHRASES,
                "Petty cash / reimbursement:",
                new String[]{
                        "Submit a claim under HR → Petty Cash (or Reimbursement, depending on your role)",
                        "It goes through the approval workflow from there"
                }, null));

        kb.add(new Entry(
                new String[]{"role", "roles", "engineer", "procurement", "admin", "ceo", "who", "permission"}, NO_PHRASES,
                "Roles in the app:",
                new String[]{
                        "Engineer / Project Manager (\"Site Team\") — raise PRs, manage attendance/leave",
                        "OH — approves new PRs",
                        "Procurement — assigns vendors, generates documents",
                        "VP — approves documents",
                        "Admin — full access",
                        "CEO — mostly read/oversight access"
                }, null));

        kb.add(new Entry(
                new String[]{"total", "prs", "count", "stat", "tile", "closed", "open"}, NO_PHRASES,
                "About the \"Total PRs\" tile:",
                new String[]{
                        "Always shows the true overall count (Closed + Open)",
                        "Does not change when you filter by clicking the Closed or Open tiles",
                        "Those two are independent counts of the same total"
                }, null));

        kb.add(new Entry(
                new String[]{"vendors", "manage", "master", "gst", "approved"}, NO_PHRASES,
                "Managing vendors:",
                new String[]{
                        "Admin and Procurement manage the vendor list under the Vendors tab",
                        "Includes GST, contact, address, bank details",
                        "A vendor must be Approved before it can be assigned to a PR"
                }, null));

        // ── Scenario / "what if" doubts — phrases give these priority over the
        // broader entries above when the question is clearly about that scenario.
        kb.add(new Entry(
                new String[]{"partially", "partial", "multivendor", "few"},
                new String[]{"partially issued", "some vendors", "not all vendors", "some approved", "few vendors"},
                "If a PO/WO/JO is split across multiple vendors and only some are VP-approved so far:",
                new String[]{
                        "It shows a \"⚠️ Partially Issued\" badge instead of a plain \"Doc Issued\" badge",
                        "Each vendor's sub-document is tracked and approved independently",
                        "The document is only fully \"Issued\" once every vendor's portion is VP-approved"
                }, null));

        kb.add(new Entry(
                new String[]{"revision", "resubmit", "revise", "requested"},
                new String[]{"revision", "send it back", "needs changes", "asked me to change"},
                "If the VP requests a revision on a document instead of approving it:",
                new String[]{
                        "It's marked \"Revision Requested\" and sent back to Procurement",
                        "The VP's comments are shown on the entry so Procurement knows what to fix",
                        "Procurement edits the document and saves",
                        "Saving after a revision request automatically resubmits it for VP approval — no separate submit step needed"
                }, null));

        kb.add(new Entry(
                new String[]{"rejected", "rejects", "rejection", "declined"},
                new String[]{"not approved", "rejected", "reject", "declined", "turned down"},
                "If the VP marks a document Not Approved instead of approving it:",
                new String[]{
                        "The dependency moves back to \"Procurement\"",
                        "Procurement can reassign the vendor, edit the document, and resubmit"
                }, null));

        kb.add(new Entry(
                new String[]{"dropdown", "missing", "appear", "showing", "notshowing"},
                new String[]{"the dropdown", "not showing", "doesn't appear", "don't see", "can't find", "missing from"},
                "If a vendor doesn't show up in the vendor-assignment dropdown:",
                new String[]{
                        "Only vendors with status \"Approved\" appear in the dropdown",
                        "Check the Vendors tab and confirm the vendor has been approved",
                        "Admin or Procurement can approve a pending vendor from there"
                }, null));

        kb.add(new Entry(
                new String[]{"wrong", "mistake", "correct", "fix", "changed"},
                new String[]{"wrong vendor", "by mistake", "made a mistake", "assigned the wrong"},
                "If you assigned the wrong vendor or need to fix item details on a PO/WO/JO:",
                new String[]{
                        "As long as it hasn't been VP-approved yet, open the document and use Edit mode",
                        "Change items, rates, or other details and save again",
                        "Once VP has approved it, the document is locked — you'd need a new document instead"
                }, null));

        kb.add(new Entry(
                new String[]{"unclub", "declub", "separate", "remove", "clubbed"},
                new String[]{"unclub", "un-club", "un club", "remove from the club", "separate the items"},
                "To unclub a PR (Admin/Procurement only):",
                new String[]{
                        "Find the ✂️ Unclub button next to its \"📎 Clubs #...\" badge — either on the PR's own row, or next to each clubbed ID listed on the primary document's row",
                        "Confirming pulls that PR back out as an independent entry, ready to be assigned to a vendor on its own",
                        "This does not remove its line item from the primary document's PDF — open that document and edit the item rows too if you also want it off the paperwork"
                }, null));

        kb.add(new Entry(
                new String[]{"attachment", "addfile", "editbug", "reattach"},
                new String[]{"change the attachment", "edit the attachment", "update the attachment", "attachment after"},
                "About editing a bug report's attachment after submitting:",
                new String[]{
                        "The attachment can only be added when first creating the bug report",
                        "Editing an existing bug (Title/Description/Module/Severity) doesn't currently support changing the attachment",
                        "If you need to add a screenshot afterward, mention it in a comment on the bug instead"
                }, null));

        return kb;
    }
}

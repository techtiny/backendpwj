package com.pwj.tracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwj.tracker.dto.VendorRequest;
import com.pwj.tracker.model.Vendor;
import com.pwj.tracker.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VendorService {

    private final VendorRepository vendorRepository;
    private final ObjectMapper objectMapper;

    @Value("${pwj.upload.dir:uploads}")
    private String uploadDir;

    @Value("${anthropic.api.key:}")
    private String anthropicApiKey;

    private static final String ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String CLAUDE_MODEL      = "claude-opus-4-6";

    // ── Get all active approved vendors for PO/WO/JO assignment dropdown ─
    public List<Vendor> getVendors() {
        return vendorRepository.findByActiveTrueAndStatusOrderByNameAsc(Vendor.VendorStatus.APPROVED);
    }

    // ── Get all pending vendors ─────────────────────────────────────────
    public List<Vendor> getPendingVendors() {
        return vendorRepository.findByActiveTrueAndStatusOrderByCreatedAtDesc(Vendor.VendorStatus.PENDING_APPROVAL);
    }

    // ── Get all vendors with any status (for VP/Procurement status view) ─
    public List<Vendor> getAllVendorsWithStatus() {
        return vendorRepository.findAllByOrderByUpdatedAtDesc();
    }

    // ── Look up vendor by name (for document generation) ─────────────────
    public java.util.Optional<Vendor> getVendorByName(String name) {
        return vendorRepository.findByNameAndActiveTrue(name);
    }

    // ── Approve vendor ──────────────────────────────────────────────────
    @Transactional
    public Vendor approveVendor(Long id) {
        Vendor vendor = vendorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Vendor not found: " + id));
        vendor.setStatus(Vendor.VendorStatus.APPROVED);
        return vendorRepository.save(vendor);
    }

    // ── Reject vendor ───────────────────────────────────────────────────
    @Transactional
    public Vendor rejectVendor(Long id) {
        Vendor vendor = vendorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Vendor not found: " + id));
        vendor.setStatus(Vendor.VendorStatus.REJECTED);
        vendor.setActive(false);
        return vendorRepository.save(vendor);
    }

    // ── Create vendor ───────────────────────────────────────────────────
    @Transactional
    public Vendor createVendor(VendorRequest req) {
        Vendor vendor = mapToVendor(new Vendor(), req);
        vendor.setActive(true);
        return vendorRepository.save(vendor);
    }

    // ── Update vendor ───────────────────────────────────────────────────
    @Transactional
    public Vendor updateVendor(Long id, VendorRequest req) {
        Vendor vendor = vendorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Vendor not found: " + id));
        mapToVendor(vendor, req);
        return vendorRepository.save(vendor);
    }

    // ── Process image and extract vendor data via Claude API ────────────
    public Map<String, Object> processVendorImage(String imageUrl) {
        if (anthropicApiKey == null || anthropicApiKey.isBlank()) {
            throw new RuntimeException("Anthropic API key not configured. Set ANTHROPIC_API_KEY environment variable.");
        }

        try {
            // 1. Load image bytes from local uploads directory
            byte[] imageBytes = loadImageFromUrl(imageUrl);
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            String mediaType = detectMediaType(imageUrl);

            // 2. Build Claude API request JSON
            String prompt = buildExtractionPrompt();
            String requestBody = buildAnthropicRequest(base64Image, mediaType, prompt);

            // 3. Call Anthropic API
            HttpClient httpClient = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ANTHROPIC_API_URL))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", anthropicApiKey)
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Claude API error {}: {}", response.statusCode(), response.body());
                throw new RuntimeException("Extraction service returned error: " + response.statusCode());
            }

            // 4. Parse Claude response → extract text content
            String rawText = extractTextFromClaudeResponse(response.body());

            // 5. Parse extracted JSON and sanitise it
            return sanitiseExtractedData(rawText);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Image processing interrupted");
        } catch (Exception e) {
            log.error("Error processing vendor image", e);
            throw new RuntimeException("Failed to process image: " + e.getMessage());
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private Vendor mapToVendor(Vendor v, VendorRequest req) {
        v.setName(req.getName());
        v.setGstNumber(req.getGstNumber());
        v.setRatings(req.getRatings());
        v.setContactPerson(req.getContactPerson());
        v.setPhoneNumber(req.getPhoneNumber());
        v.setEmail(req.getEmail());
        v.setCategory(req.getCategory());
        v.setTags(req.getTags());
        v.setVendorDocUrl(req.getVendorDocUrl());

        v.setCompanyType(req.getCompanyType());
        v.setVendorType(req.getVendorType());
        v.setSpocName(req.getSpocName());
        v.setSpocEmail(req.getSpocEmail());
        v.setSpocPhone(req.getSpocPhone());
        v.setBranch(req.getBranch());
        v.setProductServices(req.getProductServices());
        v.setSocialMedia(req.getSocialMedia());
        v.setPanNumber(req.getPanNumber());
        v.setTanNumber(req.getTanNumber());
        v.setCinNumber(req.getCinNumber());
        v.setMsmeNumber(req.getMsmeNumber());
        v.setGstDocUrl(req.getGstDocUrl());
        v.setMsmeDocUrl(req.getMsmeDocUrl());
        v.setTanDocUrl(req.getTanDocUrl());
        v.setPanDocUrl(req.getPanDocUrl());

        if (req.getEmpanelDate() != null && !req.getEmpanelDate().isBlank()) {
            try { v.setEmpanelDate(LocalDate.parse(req.getEmpanelDate())); }
            catch (DateTimeParseException e) { log.warn("Invalid empanelDate: {}", req.getEmpanelDate()); }
        }

        v.setVendorCode(req.getVendorCode());
        v.setWebsite(req.getWebsite());
        v.setCurrency(req.getCurrency());
        v.setLanguage(req.getLanguage());
        v.setCountry(req.getCountry());
        v.setState(req.getState());
        v.setCity(req.getCity());
        v.setZipCode(req.getZipCode());
        v.setStreet(req.getStreet());
        v.setBankName(req.getBankName());
        v.setAccountNumber(req.getAccountNumber());
        v.setIfscCode(req.getIfscCode());
        v.setBankDetails(req.getBankDetails());
        v.setBankDocUrl(req.getBankDocUrl());
        v.setPaymentDetails(req.getPaymentDetails());
        v.setDeliveryTerms(req.getDeliveryTerms());
        v.setSameAddressForBillingShipping(req.getSameAddressForBillingShipping());

        // Parse joining date
        if (req.getJoiningDate() != null && !req.getJoiningDate().isBlank()) {
            try { v.setJoiningDate(LocalDate.parse(req.getJoiningDate())); }
            catch (DateTimeParseException e) { log.warn("Invalid joiningDate: {}", req.getJoiningDate()); }
        }

        // Contacts: serialise list → JSON string
        if (req.getContacts() != null) {
            try { v.setContacts(objectMapper.writeValueAsString(req.getContacts())); }
            catch (Exception e) { log.warn("Could not serialise contacts"); }
        }

        // Policies
        if (req.getMaximumReturnDays() != null && !req.getMaximumReturnDays().isBlank()) {
            try { v.setMaximumReturnDays(Integer.parseInt(req.getMaximumReturnDays())); }
            catch (NumberFormatException e) { /* ignore non-numeric */ }
        }
        v.setReturnFees(req.getReturnFees());
        v.setListVendorPolicies(req.getListVendorPolicies());
        v.setVendorPaysReturnShipping(req.getVendorPaysReturnShipping());

        // Status
        if (req.getStatus() != null) {
            try { v.setStatus(Vendor.VendorStatus.valueOf(req.getStatus())); }
            catch (IllegalArgumentException e) { v.setStatus(Vendor.VendorStatus.PENDING_APPROVAL); }
        } else if (v.getStatus() == null) {
            v.setStatus(Vendor.VendorStatus.PENDING_APPROVAL);
        }

        return v;
    }

    private byte[] loadImageFromUrl(String imageUrl) throws IOException {
        // imageUrl is like "/api/v1/upload/image/uuid.jpg"
        String filename = imageUrl.substring(imageUrl.lastIndexOf('/') + 1);
        Path filePath = Paths.get(uploadDir).resolve(filename).normalize();
        if (!Files.exists(filePath)) {
            throw new IOException("Image file not found: " + filename);
        }
        return Files.readAllBytes(filePath);
    }

    private String detectMediaType(String imageUrl) {
        String lower = imageUrl.toLowerCase();
        if (lower.endsWith(".png"))  return "image/png";
        if (lower.endsWith(".gif"))  return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    private String buildAnthropicRequest(String base64Image, String mediaType, String prompt) throws Exception {
        Map<String, Object> imageSource = Map.of(
                "type", "base64",
                "media_type", mediaType,
                "data", base64Image
        );
        Map<String, Object> imageBlock = Map.of("type", "image", "source", imageSource);
        Map<String, Object> textBlock  = Map.of("type", "text",  "text",   prompt);
        Map<String, Object> message    = Map.of("role", "user",  "content", List.of(imageBlock, textBlock));
        Map<String, Object> body       = Map.of(
                "model",      CLAUDE_MODEL,
                "max_tokens", 1024,
                "messages",   List.of(message)
        );
        return objectMapper.writeValueAsString(body);
    }

    private String buildExtractionPrompt() {
        return """
                You are a vendor document data extractor. Analyse this document image and extract all vendor-related information.

                Return ONLY a valid JSON object (no markdown, no code fences) with these exact keys (use null for fields not found in the document):
                {
                  "name": "company or vendor name",
                  "gstNumber": "GST / GSTIN / tax registration number",
                  "contactPerson": "contact person or account holder name",
                  "phoneNumber": "phone or mobile number",
                  "email": "email address",
                  "category": "business category or type",
                  "vendorCode": "vendor code or account number",
                  "website": "website URL",
                  "currency": "currency code (INR/USD/EUR etc)",
                  "language": "language",
                  "country": "country",
                  "state": "state or province",
                  "city": "city",
                  "zipCode": "zip or postal code",
                  "street": "street address",
                  "bankDetails": "bank name, account number, IFSC code, branch combined in one string",
                  "paymentDetails": "payment terms or details",
                  "deliveryTerms": "delivery terms",
                  "joiningDate": "date in YYYY-MM-DD format, or null"
                }

                Return only the JSON object, nothing else.
                """;
    }

    private String extractTextFromClaudeResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode content = root.path("content");
        if (content.isArray() && content.size() > 0) {
            return content.get(0).path("text").asText();
        }
        throw new RuntimeException("Unexpected Claude API response format");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitiseExtractedData(String rawText) {
        // Strip markdown code fences if Claude wraps the JSON
        String cleaned = rawText.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("```[a-zA-Z]*\\n?", "").replace("```", "").trim();
        }
        try {
            Map<String, Object> extracted = objectMapper.readValue(cleaned, Map.class);
            // Remove null values so frontend can detect empty extraction
            extracted.entrySet().removeIf(e -> e.getValue() == null);
            return extracted;
        } catch (Exception e) {
            log.warn("Could not parse extracted JSON: {}", cleaned);
            throw new RuntimeException("Could not parse extracted vendor data from document");
        }
    }

    public void deleteVendor(Long id) {
        if (!vendorRepository.existsById(id)) throw new RuntimeException("Vendor not found: " + id);
        vendorRepository.deleteById(id);
    }
}

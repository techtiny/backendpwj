package com.pwj.tracker.account.service;

import com.pwj.tracker.account.dto.GstImportBatchDto;
import com.pwj.tracker.account.entity.ExpenseItem;
import com.pwj.tracker.account.entity.GstImportBatch;
import com.pwj.tracker.account.repository.ExpenseItemRepository;
import com.pwj.tracker.account.repository.GstImportBatchRepository;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Imports a GSTR-2B excel (downloaded from the GST portal) and reconciles its "GSTR-2B"
 * sheet (taxable inward supplies from registered persons) against the GST tab's expense
 * records: a row matches when its Invoice No. equals the excel's Invoice number AND the
 * GST Amt (Amount Sent × GST%) equals the excel's Integrated+Central+State/UT tax, rounded
 * to the nearest rupee. A match sets gstInputStatus=true and gstInputDate from the excel's
 * GSTR-1/IFF/GSTR-5 Filing Date. Rows already marked gstInputStatus=true are never reconsidered, so
 * reimporting the same (or an updated) file is safe.
 */
@Service
public class GstImportService {

    private final ExpenseItemRepository expenseItemRepository;
    private final GstImportBatchRepository batchRepository;

    @Value("${pwj.upload.dir:uploads}")
    private String uploadDir;

    private static final DateTimeFormatter EXCEL_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public GstImportService(ExpenseItemRepository expenseItemRepository, GstImportBatchRepository batchRepository) {
        this.expenseItemRepository = expenseItemRepository;
        this.batchRepository = batchRepository;
    }

    @Transactional
    public GstImportBatchDto importGstr2b(MultipartFile file, String uploadedBy) throws IOException {
        String ext = getExtension(file.getOriginalFilename());
        String storedFilename = UUID.randomUUID() + ext;
        Path dir = Paths.get(uploadDir, "gst-imports");
        Files.createDirectories(dir);
        Files.copy(file.getInputStream(), dir.resolve(storedFilename), StandardCopyOption.REPLACE_EXISTING);

        int rowsRead = 0;
        int rowsMatched = 0;

        try (Workbook wb = WorkbookFactory.create(Files.newInputStream(dir.resolve(storedFilename)))) {
            Sheet sheet = findSheet(wb);
            if (sheet != null) {
                DataFormatter fmt = new DataFormatter();
                int[] cols = findColumns(sheet, fmt); // [headerRow, colInvoiceNo, colInvoiceDate, colIgst, colCgst, colSgst]
                if (cols != null) {
                    for (int r = cols[0] + 1; r <= sheet.getLastRowNum(); r++) {
                        Row row = sheet.getRow(r);
                        if (row == null) continue;
                        String invoiceNo = cellString(row, cols[1], fmt);
                        if (invoiceNo.isBlank()) continue; // end of the data block (totals/blank rows)
                        rowsRead++;

                        LocalDate filingDate = cellDate(row, cols[2], fmt);
                        BigDecimal igst = cellAmount(row, cols[3], fmt);
                        BigDecimal cgst = cellAmount(row, cols[4], fmt);
                        BigDecimal sgst = cellAmount(row, cols[5], fmt);
                        BigDecimal totalTax = igst.add(cgst).add(sgst).setScale(0, RoundingMode.HALF_UP);

                        List<ExpenseItem> candidates =
                                expenseItemRepository.findByGstInvoiceNoIgnoreCaseAndGstInputStatus(invoiceNo, false);
                        for (ExpenseItem e : candidates) {
                            BigDecimal gstAmt = safe(e.getSentAmount())
                                    .multiply(safe(e.getGstPercent()))
                                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                                    .setScale(0, RoundingMode.HALF_UP);
                            if (gstAmt.compareTo(totalTax) == 0) {
                                e.setGstInputStatus(true);
                                e.setGstInputDate(filingDate);
                                expenseItemRepository.save(e);
                                rowsMatched++;
                            }
                        }
                    }
                }
            }
        }

        GstImportBatch batch = new GstImportBatch();
        batch.setOriginalFilename(file.getOriginalFilename());
        batch.setStoredFilename(storedFilename);
        batch.setUploadedBy(uploadedBy);
        batch.setUploadedAt(LocalDateTime.now());
        batch.setRowsRead(rowsRead);
        batch.setRowsMatched(rowsMatched);
        batch = batchRepository.save(batch);

        return toDto(batch);
    }

    public List<GstImportBatchDto> getHistory() {
        return batchRepository.findAllByOrderByUploadedAtDesc().stream().map(this::toDto).toList();
    }

    public Path resolveStoredFile(Long batchId) {
        GstImportBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Import not found: " + batchId));
        return Paths.get(uploadDir, "gst-imports", batch.getStoredFilename());
    }

    public GstImportBatch getBatchOrThrow(Long batchId) {
        return batchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Import not found: " + batchId));
    }

    // ── Sheet / column discovery — tolerant of the standard GSTR-2B layout's exact wording ──

    private Sheet findSheet(Workbook wb) {
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            if (wb.getSheetName(i).trim().equalsIgnoreCase("GSTR-2B")) return wb.getSheetAt(i);
        }
        return null;
    }

    /** Returns [headerRow, colInvoiceNo, colFilingDate, colIgst, colCgst, colSgst], or null if not found. */
    private int[] findColumns(Sheet sheet, DataFormatter fmt) {
        int headerRow = -1, colInvoiceNo = -1, colFilingDate = -1, colIgst = -1, colCgst = -1, colSgst = -1;
        int lastScanRow = Math.min(sheet.getLastRowNum(), 20);
        for (int r = 0; r <= lastScanRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (Cell c : row) {
                String v = fmt.formatCellValue(c).trim().toLowerCase();
                if (v.equals("invoice number")) { headerRow = r; colInvoiceNo = c.getColumnIndex(); }
                else if (v.contains("filing date")) colFilingDate = c.getColumnIndex();
                else if (v.contains("integrated tax")) colIgst = c.getColumnIndex();
                else if (v.contains("central tax")) colCgst = c.getColumnIndex();
                else if (v.contains("state/ut tax") || v.contains("state /ut tax")) colSgst = c.getColumnIndex();
            }
            if (headerRow != -1 && colFilingDate != -1 && colIgst != -1 && colCgst != -1 && colSgst != -1) {
                return new int[]{headerRow, colInvoiceNo, colFilingDate, colIgst, colCgst, colSgst};
            }
        }
        return null;
    }

    private String cellString(Row row, int col, DataFormatter fmt) {
        if (col < 0) return "";
        Cell c = row.getCell(col);
        return c == null ? "" : fmt.formatCellValue(c).trim();
    }

    private LocalDate cellDate(Row row, int col, DataFormatter fmt) {
        if (col < 0) return null;
        Cell c = row.getCell(col);
        if (c == null) return null;
        try {
            if (c.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(c)) {
                return c.getLocalDateTimeCellValue().toLocalDate();
            }
            String s = fmt.formatCellValue(c).trim();
            return s.isBlank() ? null : LocalDate.parse(s, EXCEL_DATE);
        } catch (Exception ex) {
            return null;
        }
    }

    private BigDecimal cellAmount(Row row, int col, DataFormatter fmt) {
        if (col < 0) return BigDecimal.ZERO;
        Cell c = row.getCell(col);
        if (c == null) return BigDecimal.ZERO;
        try {
            if (c.getCellType() == CellType.NUMERIC) return BigDecimal.valueOf(c.getNumericCellValue());
            String s = fmt.formatCellValue(c).replace(",", "").trim();
            return s.isBlank() || s.equals("-") ? BigDecimal.ZERO : new BigDecimal(s);
        } catch (Exception ex) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal safe(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return ".xlsx";
        return filename.substring(filename.lastIndexOf("."));
    }

    private GstImportBatchDto toDto(GstImportBatch b) {
        GstImportBatchDto d = new GstImportBatchDto();
        d.setId(b.getId());
        d.setOriginalFilename(b.getOriginalFilename());
        d.setUploadedBy(b.getUploadedBy());
        d.setUploadedAt(b.getUploadedAt());
        d.setRowsRead(b.getRowsRead());
        d.setRowsMatched(b.getRowsMatched());
        d.setDownloadUrl("/api/v1/gst-import/" + b.getId() + "/file");
        return d;
    }
}

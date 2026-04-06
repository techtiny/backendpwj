package com.pwj.tracker.service;

import com.pwj.tracker.model.PwjEntry;
import com.pwj.tracker.repository.PwjEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelExportService {

    private final PwjEntryRepository repository;

    private static final String[] HEADERS = {
        "ID", "Date", "Raised By", "Project", "BOQ No", "Material Required",
        "Specification", "Brand", "Unit", "Quantity", "Date of Requirement",
        "Image Reference", "Approval", "Vendor", "PWJ Issued", "Status", "Delivered Date",
        "Remarks", "Approved By", "Approved At", "Approval Comment"
    };

    public byte[] generateWeeklyReport() {
        List<PwjEntry> entries = repository.findAll(org.springframework.data.domain.Sort.by("id").ascending());

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("PWJ Report");

            // ── Title row ──
            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleFont.setColor(IndexedColors.WHITE.getIndex());
            titleStyle.setFont(titleFont);
            titleStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            titleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            titleStyle.setAlignment(HorizontalAlignment.CENTER);

            Row titleRow = sheet.createRow(0);
            titleRow.setHeightInPoints(28);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("PWJ Tracker — Weekly Report (" + LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy")) + ")");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, HEADERS.length - 1));

            // ── Header row ──
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            Row headerRow = sheet.createRow(1);
            headerRow.setHeightInPoints(18);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            // ── Data rows ──
            CellStyle evenStyle = workbook.createCellStyle();
            evenStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            evenStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            DateTimeFormatter df  = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            int rowNum = 2;
            for (PwjEntry e : entries) {
                Row row = sheet.createRow(rowNum);
                if (rowNum % 2 == 0) {
                    for (int c = 0; c < HEADERS.length; c++) row.createCell(c).setCellStyle(evenStyle);
                }

                setValue(row, 0,  e.getId() != null ? e.getId().toString() : "");
                setValue(row, 1,  e.getTimestamp() != null ? e.getTimestamp().format(dtf) : "");
                setValue(row, 2,  e.getRaisedBy());
                setValue(row, 3,  e.getProjectName());
                setValue(row, 4,  e.getBoqNo());
                setValue(row, 5,  e.getMaterialRequired());
                setValue(row, 6,  e.getSpecification());
                setValue(row, 7,  e.getBrand());
                setValue(row, 8,  e.getUnit());
                setValue(row, 9,  e.getQuantity() != null ? e.getQuantity().toString() : "");
                setValue(row, 10, e.getDateOfRequirement() != null ? e.getDateOfRequirement().format(df) : "");
                setValue(row, 11, e.getImageReference());
                setValue(row, 12, e.getApprovalStatus() != null ? e.getApprovalStatus().name() : "");
                setValue(row, 13, e.getVendor());
                setValue(row, 14, Boolean.TRUE.equals(e.getPwjIssued()) ? "Yes" : "No");
                setValue(row, 15, e.getStatus() != null ? e.getStatus().name() : "");
                setValue(row, 16, e.getDeliveredDate() != null ? e.getDeliveredDate().format(df) : "");
                setValue(row, 17, e.getRemarks());
                setValue(row, 18, e.getApprovedBy());
                setValue(row, 19, e.getApprovedAt() != null ? e.getApprovedAt().format(dtf) : "");
                setValue(row, 20, e.getApprovalComment());
                rowNum++;
            }

            // ── Auto-size columns ──
            for (int i = 0; i < HEADERS.length; i++) sheet.autoSizeColumn(i);

            workbook.write(out);
            log.info("Excel report generated: {} entries", entries.size());
            return out.toByteArray();

        } catch (Exception ex) {
            throw new RuntimeException("Failed to generate Excel report", ex);
        }
    }

    private void setValue(Row row, int col, String value) {
        Cell cell = row.getCell(col);
        if (cell == null) cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
    }
}

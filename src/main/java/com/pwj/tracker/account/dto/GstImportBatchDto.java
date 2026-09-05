package com.pwj.tracker.account.dto;

import java.time.LocalDateTime;

public class GstImportBatchDto {
    private Long id;
    private String originalFilename;
    private String uploadedBy;
    private LocalDateTime uploadedAt;
    private Integer rowsRead;
    private Integer rowsMatched;
    private String downloadUrl;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
    public Integer getRowsRead() { return rowsRead; }
    public void setRowsRead(Integer rowsRead) { this.rowsRead = rowsRead; }
    public Integer getRowsMatched() { return rowsMatched; }
    public void setRowsMatched(Integer rowsMatched) { this.rowsMatched = rowsMatched; }
    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }
}

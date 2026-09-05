package com.pwj.tracker.account.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

// One row per GSTR-2B excel import — kept as an audit/history trail. The uploaded file
// itself is stored under pwj.upload.dir/gst-imports so it can be re-downloaded later.
@Entity
@Table(name = "gst_import_batch")
public class GstImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(name = "stored_filename", length = 255)
    private String storedFilename;

    @Column(name = "uploaded_by", length = 150)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "rows_read")
    private Integer rowsRead;

    @Column(name = "rows_matched")
    private Integer rowsMatched;

    public GstImportBatch() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    public String getStoredFilename() { return storedFilename; }
    public void setStoredFilename(String storedFilename) { this.storedFilename = storedFilename; }
    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
    public Integer getRowsRead() { return rowsRead; }
    public void setRowsRead(Integer rowsRead) { this.rowsRead = rowsRead; }
    public Integer getRowsMatched() { return rowsMatched; }
    public void setRowsMatched(Integer rowsMatched) { this.rowsMatched = rowsMatched; }
}

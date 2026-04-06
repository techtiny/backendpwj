-- ============================================
-- PWJ TRACKER - MySQL Production Schema
-- Run this once before starting the app with prod profile
-- ============================================

CREATE DATABASE IF NOT EXISTS pwj_tracker
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE pwj_tracker;

CREATE TABLE IF NOT EXISTS pwj_entry (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    timestamp           DATETIME,
    raised_by           VARCHAR(100)    NOT NULL,
    project_name        VARCHAR(200)    NOT NULL,
    boq_no              VARCHAR(50),
    material_required   VARCHAR(300)    NOT NULL,
    specification       TEXT,
    brand               VARCHAR(100),
    unit                VARCHAR(50),
    quantity            DECIMAL(12, 2),
    date_of_requirement DATE,
    image_reference     VARCHAR(500),
    approval_status     ENUM('PROCEED','HOLD','NOT_APPROVED'),
    vendor              VARCHAR(200),
    pwj_issued          BOOLEAN         DEFAULT FALSE,
    status              ENUM('OPEN','CLOSED') DEFAULT 'OPEN',
    delivered_date      DATE,
    remarks             TEXT,
    approval_comment    VARCHAR(500),
    approved_by         VARCHAR(100),
    approved_at         DATETIME,
    created_at          DATETIME        DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_project       (project_name),
    INDEX idx_status        (status),
    INDEX idx_approval      (approval_status),
    INDEX idx_raised_by     (raised_by),
    INDEX idx_created_at    (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- Trigger: auto-set updated_at
-- ============================================
DELIMITER $$
CREATE TRIGGER IF NOT EXISTS trg_pwj_entry_updated
BEFORE UPDATE ON pwj_entry
FOR EACH ROW
BEGIN
    SET NEW.updated_at = NOW();
END $$
DELIMITER ;

-- SentinelAI :: reports — generated report files and simple daily/weekly schedules.
CREATE TABLE report_schedules (
    id           BIGINT                                                                  NOT NULL AUTO_INCREMENT,
    org_id       BIGINT                                                                  NOT NULL,
    name         VARCHAR(100)                                                            NOT NULL,
    report_type  ENUM('INCIDENT_SUMMARY','TOP_ATTACKERS','ALERTS_BY_MITRE','RESPONSE_ACTIONS') NOT NULL,
    format       ENUM('PDF','CSV')                                                       NOT NULL,
    frequency    ENUM('DAILY','WEEKLY')                                                  NOT NULL,
    day_of_week  TINYINT                                                                 NULL,
    hour_utc     TINYINT                                                                 NOT NULL,
    enabled      TINYINT(1)                                                              NOT NULL DEFAULT 1,
    last_run_at  DATETIME(6)                                                             NULL,
    next_run_at  DATETIME(6)                                                             NOT NULL,
    created_by   BIGINT                                                                  NULL,
    created_at   DATETIME(6)                                                             NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_report_schedules_due (enabled, next_run_at),
    CONSTRAINT fk_report_schedules_org FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    CONSTRAINT fk_report_schedules_user FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE generated_reports (
    id           BIGINT                                                                  NOT NULL AUTO_INCREMENT,
    org_id       BIGINT                                                                  NOT NULL,
    report_type  ENUM('INCIDENT_SUMMARY','TOP_ATTACKERS','ALERTS_BY_MITRE','RESPONSE_ACTIONS') NOT NULL,
    format       ENUM('PDF','CSV')                                                       NOT NULL,
    range_from   DATETIME(6)                                                             NOT NULL,
    range_to     DATETIME(6)                                                             NOT NULL,
    status       ENUM('COMPLETED','FAILED')                                              NOT NULL,
    row_count    INT                                                                     NOT NULL DEFAULT 0,
    size_bytes   INT                                                                     NOT NULL DEFAULT 0,
    content      LONGBLOB                                                                NULL,
    error        VARCHAR(500)                                                            NULL,
    schedule_id  BIGINT                                                                  NULL,
    created_by   BIGINT                                                                  NULL,
    created_at   DATETIME(6)                                                             NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_generated_reports_org (org_id, created_at),
    CONSTRAINT fk_generated_reports_org FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    CONSTRAINT fk_generated_reports_schedule FOREIGN KEY (schedule_id) REFERENCES report_schedules (id) ON DELETE SET NULL,
    CONSTRAINT fk_generated_reports_user FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

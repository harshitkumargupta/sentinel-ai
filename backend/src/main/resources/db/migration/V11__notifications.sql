-- SentinelAI :: notifications (notification module)
CREATE TABLE notifications (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    incident_id BIGINT       NULL,
    message     VARCHAR(500) NOT NULL,
    read_flag   TINYINT(1)   NOT NULL DEFAULT 0,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_notifications_user (user_id),
    KEY idx_notifications_incident (incident_id),
    KEY idx_notifications_read (read_flag),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_notifications_incident FOREIGN KEY (incident_id) REFERENCES incidents (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

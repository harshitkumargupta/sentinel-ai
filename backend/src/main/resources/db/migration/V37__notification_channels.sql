-- SentinelAI :: notification channels (in-app / email / webhook), rules that route incidents to
-- them, and a delivery log (attempts, failures, mock deliveries).
CREATE TABLE notification_channels (
    id         BIGINT                              NOT NULL AUTO_INCREMENT,
    org_id     BIGINT                              NOT NULL,
    name       VARCHAR(100)                        NOT NULL,
    type       ENUM('IN_APP','EMAIL','WEBHOOK')    NOT NULL,
    target     VARCHAR(500)                        NULL,
    enabled    TINYINT(1)                          NOT NULL DEFAULT 1,
    created_at DATETIME(6)                         NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_notif_channels_org FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE notification_rules (
    id                  BIGINT                                  NOT NULL AUTO_INCREMENT,
    org_id              BIGINT                                  NOT NULL,
    name                VARCHAR(100)                            NOT NULL,
    min_severity        ENUM('LOW','MEDIUM','HIGH','CRITICAL')  NOT NULL DEFAULT 'HIGH',
    rule_type           VARCHAR(50)                             NULL,
    on_incident_created TINYINT(1)                              NOT NULL DEFAULT 1,
    on_escalation       TINYINT(1)                              NOT NULL DEFAULT 1,
    channel_ids         JSON                                    NOT NULL,
    enabled             TINYINT(1)                              NOT NULL DEFAULT 1,
    created_at          DATETIME(6)                             NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_notif_rules_org FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE notification_deliveries (
    id          BIGINT                                     NOT NULL AUTO_INCREMENT,
    org_id      BIGINT                                     NOT NULL,
    channel_id  BIGINT                                     NULL,
    rule_id     BIGINT                                     NULL,
    incident_id BIGINT                                     NULL,
    subject     VARCHAR(255)                               NOT NULL,
    status      ENUM('SENT','MOCKED','FAILED')             NOT NULL,
    attempts    INT                                        NOT NULL DEFAULT 0,
    last_error  VARCHAR(500)                               NULL,
    test        TINYINT(1)                                 NOT NULL DEFAULT 0,
    created_at  DATETIME(6)                                NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_notif_deliveries_org (org_id, created_at),
    CONSTRAINT fk_notif_deliveries_channel FOREIGN KEY (channel_id) REFERENCES notification_channels (id) ON DELETE SET NULL,
    CONSTRAINT fk_notif_deliveries_rule FOREIGN KEY (rule_id) REFERENCES notification_rules (id) ON DELETE SET NULL,
    CONSTRAINT fk_notif_deliveries_incident FOREIGN KEY (incident_id) REFERENCES incidents (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Works out of the box: in-app delivery of critical incidents.
INSERT INTO notification_channels (org_id, name, type, target) VALUES (1, 'In-app (analysts & admins)', 'IN_APP', NULL);
INSERT INTO notification_rules (org_id, name, min_severity, on_incident_created, on_escalation, channel_ids)
VALUES (1, 'Critical incidents', 'CRITICAL', 1, 1, JSON_ARRAY(LAST_INSERT_ID()));

-- SentinelAI :: QRadar-style reference sets (watchlists): named lists of IPs / users / strings that
-- building blocks test with IN_REFERENCE_SET. IP sets may hold single IPv4 addresses or CIDRs.
CREATE TABLE reference_sets (
    id           BIGINT                            NOT NULL AUTO_INCREMENT,
    org_id       BIGINT                            NOT NULL,
    name         VARCHAR(100)                      NOT NULL,
    element_type ENUM('IP','USERNAME','TEXT')      NOT NULL,
    description  VARCHAR(500)                      NULL,
    created_at   DATETIME(6)                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_reference_sets_org_name (org_id, name),
    CONSTRAINT fk_reference_sets_org FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE reference_set_items (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    set_id     BIGINT       NOT NULL,
    value      VARCHAR(255) NOT NULL,
    note       VARCHAR(255) NULL,
    added_by   BIGINT       NULL,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_reference_set_items (set_id, value),
    CONSTRAINT fk_reference_set_items_set FOREIGN KEY (set_id) REFERENCES reference_sets (id) ON DELETE CASCADE,
    CONSTRAINT fk_reference_set_items_user FOREIGN KEY (added_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO reference_sets (org_id, name, element_type, description) VALUES
    (1, 'Watchlist IPs', 'IP', 'Source IPs analysts want flagged in rules.'),
    (1, 'Watchlist users', 'USERNAME', 'Accounts under heightened monitoring.'),
    (1, 'Blocked IPs', 'IP', 'Addresses blocked at the perimeter.');

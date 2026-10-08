-- SentinelAI :: QRadar-style building blocks — named, reusable event conditions that detection rules
-- reference by name in their config ({"buildingBlocks":["..."]}). A rule only evaluates an event that
-- matches every building block it references. Conditions are AND-ed: [{field, op, values}].
CREATE TABLE building_blocks (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    org_id      BIGINT       NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    conditions  JSON         NOT NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_building_blocks_org_name (org_id, name),
    CONSTRAINT fk_building_blocks_org FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO building_blocks (org_id, name, description, conditions) VALUES
    (1, 'External source IP', 'Source IP is outside the private/loopback ranges.',
     '[{"field":"sourceIp","op":"NOT_IN_CIDR","values":["10.0.0.0/8","172.16.0.0/12","192.168.0.0/16","127.0.0.0/8"]}]'),
    (1, 'Privileged account', 'Username is a well-known privileged account.',
     '[{"field":"username","op":"IN","values":["root","admin","administrator","sa"]}]'),
    (1, 'Failed outcome', 'The action failed (denied / bad credentials).',
     '[{"field":"outcome","op":"EQUALS","values":["FAILURE"]}]'),
    (1, 'Outside business hours', 'Event hour (UTC) is before 08:00 or from 19:00.',
     '[{"field":"hour","op":"NOT_IN","values":["8","9","10","11","12","13","14","15","16","17","18"]}]');

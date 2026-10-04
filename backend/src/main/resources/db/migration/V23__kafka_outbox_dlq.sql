-- SentinelAI :: Phase 11 — Kafka pipeline persistence.
--   outbox             transactional outbox: event + outbox row written in one tx, relayed to Kafka.
--   processed_messages consumer-side idempotency: (consumer_group, message_id) seen-set.
--   dlq_messages       dead letters persisted from events.dlq for the admin API / replay.

CREATE TABLE outbox (
    id             BIGINT                           NOT NULL AUTO_INCREMENT,
    aggregate_type VARCHAR(50)                      NOT NULL,
    aggregate_id   BIGINT                           NOT NULL,
    topic          VARCHAR(100)                     NOT NULL,
    kafka_key      VARCHAR(255)                     NULL,
    payload        JSON                             NOT NULL,
    status         ENUM('PENDING','SENT','FAILED')  NOT NULL DEFAULT 'PENDING',
    attempts       INT                              NOT NULL DEFAULT 0,
    created_at     DATETIME(6)                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at   DATETIME(6)                      NULL,
    PRIMARY KEY (id),
    KEY idx_outbox_pending (status, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE processed_messages (
    consumer_group VARCHAR(100) NOT NULL,
    message_id     VARCHAR(100) NOT NULL,
    processed_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (consumer_group, message_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE dlq_messages (
    id             BIGINT                      NOT NULL AUTO_INCREMENT,
    original_topic VARCHAR(100)                NOT NULL,
    handler        VARCHAR(50)                 NULL,
    message_id     VARCHAR(100)                NULL,
    kafka_key      VARCHAR(255)                NULL,
    payload        JSON                        NULL,
    attempts       INT                         NOT NULL DEFAULT 0,
    error          VARCHAR(1000)               NULL,
    headers        JSON                        NULL,
    status         ENUM('DEAD','REPLAYED')     NOT NULL DEFAULT 'DEAD',
    created_at     DATETIME(6)                 NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    replayed_at    DATETIME(6)                 NULL,
    PRIMARY KEY (id),
    KEY idx_dlq_status (status, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

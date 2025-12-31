
CREATE TABLE IF NOT EXISTS webhook_outbox (
                                id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
                                event_type ENUM('INSERT','UPDATE','DELETE') NOT NULL,
                                table_name VARCHAR(64) NOT NULL,
                                pk VARCHAR(255) NOT NULL,
                                payload JSON NOT NULL,
                                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                status ENUM('NEW','SENDING','SENT','FAILED') NOT NULL DEFAULT 'NEW',
                                attempts INT NOT NULL DEFAULT 0,
                                next_retry_at TIMESTAMP NULL DEFAULT NULL,
                                last_error TEXT NULL,
                                sent_at TIMESTAMP NULL DEFAULT NULL,
                                correlation_id CHAR(36) NULL
);
CREATE INDEX idx_outbox_status_retry ON webhook_outbox(status, next_retry_at, id);


insert into webhook_outbox (id, event_type, table_name, pk, payload, created_at, status, attempts, next_retry_at, last_error, sent_at, correlation_id) values (1, 'UPDATE', 'einsatzmittel', 'BN EAL Innenstadt', '{"RUFNAME": "BN EAL Innenstadt"}', '2025-12-30 22:48:23', 'NEW', 0, null, null, null, '40424816-e5c9-11f0-9c3f-54bf649d29d4');
insert into webhook_outbox (id, event_type, table_name, pk, payload, created_at, status, attempts, next_retry_at, last_error, sent_at, correlation_id) values (2, 'UPDATE', 'einsatzmittel', 'BN EAL Innenstadt', '{"RUFNAME": "BN EAL Innenstadt"}', '2025-12-30 22:48:30', 'NEW', 0, null, null, null, '44e7df2e-e5c9-11f0-9c3f-54bf649d29d4');

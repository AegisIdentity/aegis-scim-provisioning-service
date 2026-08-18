--
-- Outbound provisioning tasks — the SCIM service's reaction to identity.user.created events.
-- A worker (follow-up) processes PENDING tasks by pushing users to downstream SCIM connectors.
-- Unique aegis_user_id makes Kafka at-least-once redelivery idempotent (no duplicate task).
--
CREATE TABLE IF NOT EXISTS outbound_provisioning_task (
    id            uuid         NOT NULL,
    tenant_id     varchar(64)  NOT NULL,
    aegis_user_id varchar(64)  NOT NULL,
    username      varchar(256) NOT NULL,
    email         varchar(320),
    status        varchar(16)  NOT NULL,
    created_at    timestamptz  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_outbound_task_user UNIQUE (aegis_user_id)
);
CREATE INDEX IF NOT EXISTS ix_outbound_task_tenant ON outbound_provisioning_task (tenant_id, created_at);

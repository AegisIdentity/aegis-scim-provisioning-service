--
-- Baseline schema for scim-provisioning-service.
--
-- GENERATED from the JPA entities by Hibernate's schema exporter, not hand-written. The service
-- runs with ddl-auto: validate, so any drift between this file and the entities fails startup —
-- generating it is what guarantees the two agree.
--
-- Regenerate after an entity change (then add a NEW V<n>__ migration; never edit an applied one):
--   mvn -o verify -Dit.test=<AnIT> -DfailIfNoSpecifiedTests=false \
--     -Dspring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=create \
--     -Dspring.jpa.properties.jakarta.persistence.schema-generation.scripts.create-target=target/generated-schema.sql
--
-- Existing (pre-Flyway) databases are handled by flyway.baseline-on-migrate=true: they are marked
-- at the baseline version and this migration is skipped, since their tables already exist.
--
create table scim_connector (enabled boolean not null, created_at timestamp(6) with time zone not null, expires_at timestamp(6) with time zone, last_used_at timestamp(6) with time zone, previous_token_expires_at timestamp(6) with time zone, id uuid not null, previous_token_hash varchar(64), tenant_id varchar(64) not null, token_hash varchar(64) not null unique, name varchar(128) not null, primary key (id));

create table scim_user (active boolean not null, created_at timestamp(6) with time zone not null, updated_at timestamp(6) with time zone not null, id uuid not null, aegis_user_id varchar(64), tenant_id varchar(64) not null, family_name varchar(128), given_name varchar(128), external_id varchar(256), user_name varchar(256) not null, email varchar(320), primary key (id), constraint uq_scim_user_tenant_username unique (tenant_id, user_name));


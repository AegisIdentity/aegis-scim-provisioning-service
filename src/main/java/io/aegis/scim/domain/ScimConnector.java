package io.aegis.scim.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A per-tenant SCIM connector: the credential an upstream IdP (Entra/Okta/Workday) presents when it
 * pushes users into a tenant over SCIM. The upstream is configured with an opaque bearer token; only
 * the SHA-256 {@code tokenHash} is stored here (the raw token is shown once, at creation, then never
 * again). SCIM requests are authenticated by hashing the presented bearer and matching an enabled row.
 */
@Entity
@Table(name = "scim_connector")
public class ScimConnector {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false, length = 64)
    private String tenantId;

    @Column(nullable = false, length = 128)
    private String name;

    /** SHA-256 hex of the opaque bearer token. The raw token is never persisted. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ScimConnector() {
    }

    public ScimConnector(UUID id, String tenantId, String name, String tokenHash) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.tokenHash = tokenHash;
    }

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

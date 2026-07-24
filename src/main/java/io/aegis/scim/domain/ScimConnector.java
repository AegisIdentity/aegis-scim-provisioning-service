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
 *
 * <p>M-svc-4: a connector token can optionally {@link #expiresAt expire}, can be {@link #rotate rotated}
 * (a new token is issued while the {@code previousTokenHash} stays valid for a short grace window so the
 * upstream can be reconfigured without downtime), and records its {@link #lastUsedAt last use}.
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

    /** SHA-256 hex of the immediately-previous token, valid until {@link #previousTokenExpiresAt} after a
     *  rotation. NOT unique (it coexists with the new {@code tokenHash} during the grace window). */
    @Column(name = "previous_token_hash", length = 64)
    private String previousTokenHash;

    @Column(name = "previous_token_expires_at")
    private Instant previousTokenExpiresAt;

    /** Optional absolute expiry of the connector's token(s); {@code null} = never expires. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /** Last time a request successfully authenticated with this connector; {@code null} until first use. */
    @Column(name = "last_used_at")
    private Instant lastUsedAt;

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

    public String getPreviousTokenHash() {
        return previousTokenHash;
    }

    public Instant getPreviousTokenExpiresAt() {
        return previousTokenExpiresAt;
    }

    /** True if the previous (pre-rotation) token is still within its grace window at {@code now}. */
    public boolean isPreviousTokenValid(Instant now) {
        return previousTokenHash != null && previousTokenExpiresAt != null
                && now.isBefore(previousTokenExpiresAt);
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    /** True if the connector's token has an absolute expiry that has passed. */
    public boolean isExpired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void markUsed(Instant when) {
        this.lastUsedAt = when;
    }

    /**
     * Rotate to a new token: the current {@code tokenHash} becomes the previous token (valid until
     * {@code graceUntil}), and {@code newTokenHash} becomes the active token. During the grace window both
     * authenticate, so the upstream can be reconfigured without downtime.
     */
    public void rotate(String newTokenHash, Instant graceUntil) {
        this.previousTokenHash = this.tokenHash;
        this.previousTokenExpiresAt = graceUntil;
        this.tokenHash = newTokenHash;
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

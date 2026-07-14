package io.aegis.scim.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/**
 * A user provisioned into a tenant over SCIM. The {@code id} is the SCIM resource id (returned to the
 * upstream IdP). {@code userName} is unique within a tenant. {@code aegisUserId} is the id returned by
 * identity-service once the user is forwarded there. Always tenant-scoped — no cross-tenant reads.
 */
@Entity
@Table(name = "scim_user", uniqueConstraints = {
        @UniqueConstraint(name = "uq_scim_user_tenant_username", columnNames = {"tenant_id", "user_name"})
})
public class ScimUser {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false, length = 64)
    private String tenantId;

    /** The IdP's own id for the user (SCIM externalId), if it sends one. */
    @Column(name = "external_id", length = 256)
    private String externalId;

    @Column(name = "user_name", nullable = false, length = 256)
    private String userName;

    @Column(length = 320)
    private String email;

    @Column(name = "given_name", length = 128)
    private String givenName;

    @Column(name = "family_name", length = 128)
    private String familyName;

    @Column(nullable = false)
    private boolean active = true;

    /** The id identity-service returned for this user; null until the forward succeeds. */
    @Column(name = "aegis_user_id", length = 64)
    private String aegisUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ScimUser() {
    }

    public ScimUser(UUID id, String tenantId, String userName) {
        this.id = id;
        this.tenantId = tenantId;
        this.userName = userName;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getGivenName() {
        return givenName;
    }

    public void setGivenName(String givenName) {
        this.givenName = givenName;
    }

    public String getFamilyName() {
        return familyName;
    }

    public void setFamilyName(String familyName) {
        this.familyName = familyName;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getAegisUserId() {
        return aegisUserId;
    }

    public void setAegisUserId(String aegisUserId) {
        this.aegisUserId = aegisUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

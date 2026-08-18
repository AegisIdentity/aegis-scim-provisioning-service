package io.aegis.scim.outbound;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/**
 * An outbound provisioning task — the SCIM service's reaction to an {@code identity.user.created}
 * event. When a user is created in the identity directory, this records that the user must be
 * provisioned <em>out</em> to the tenant's configured downstream applications (the Okta "Lifecycle
 * Management → outbound provisioning" analogue).
 *
 * <p>This models the choreography end: an identity event, consumed here, becomes a durable task. A
 * worker then processes {@code PENDING} tasks by pushing the user to each configured downstream SCIM
 * connector. <b>That downstream push is the last mile</b> and is not exercised in the local stack
 * (there is no external SCIM target to push to) — the task creation is the demonstrable, verifiable
 * cross-service reaction; the push worker is a documented follow-up.
 *
 * <p>Idempotent: a unique constraint on {@code aegis_user_id} means a redelivered event (Kafka is
 * at-least-once) does not create a duplicate task.
 */
@Entity
@Table(name = "outbound_provisioning_task",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_outbound_task_user", columnNames = "aegis_user_id"))
public class OutboundProvisioningTask {

    public enum Status { PENDING, PUSHED, FAILED }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "aegis_user_id", nullable = false, length = 64)
    private String aegisUserId;

    @Column(name = "username", nullable = false, length = 256)
    private String username;

    @Column(name = "email", length = 320)
    private String email;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OutboundProvisioningTask() {
        // JPA
    }

    public OutboundProvisioningTask(String tenantId, String aegisUserId, String username, String email) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.aegisUserId = aegisUserId;
        this.username = username;
        this.email = email;
        this.status = Status.PENDING;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getAegisUserId() {
        return aegisUserId;
    }

    public String getUsername() {
        return username;
    }

    public Status getStatus() {
        return status;
    }
}

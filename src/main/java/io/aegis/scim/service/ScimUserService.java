package io.aegis.scim.service;

import io.aegis.scim.domain.ScimUser;
import io.aegis.scim.domain.ScimUserRepository;
import io.aegis.scim.service.ScimExceptions.DuplicateScimUserException;
import io.aegis.scim.service.ScimExceptions.ProvisioningFailedException;
import io.aegis.scim.service.ScimExceptions.ScimBadRequestException;
import io.aegis.scim.service.ScimExceptions.ScimUserNotFoundException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Tenant-scoped SCIM user provisioning. Users are stored locally AND forwarded to identity-service:
 * on create the user is provisioned there (and the returned id stored on {@code aegisUserId}); on
 * deactivation/reactivation identity-service is told to disable/enable. Every operation carries a
 * tenant (resolved from the connector token) — no cross-tenant reads.
 */
@Service
public class ScimUserService {

    private static final Logger log = LoggerFactory.getLogger(ScimUserService.class);

    private final ScimUserRepository users;
    private final ScimIdentityClient identityClient;

    public ScimUserService(ScimUserRepository users, ScimIdentityClient identityClient) {
        this.users = users;
        this.identityClient = identityClient;
    }

    /** A normalized view of the inbound SCIM User attributes the service acts on. */
    public record UserInput(String externalId, String userName, String email, String givenName,
                            String familyName, boolean active) {
    }

    @Transactional
    public ScimUser create(String tenantId, UserInput input) {
        requireTenant(tenantId);
        if (!StringUtils.hasText(input.userName())) {
            throw new ScimBadRequestException("userName is required");
        }
        if (users.existsByTenantIdAndUserName(tenantId, input.userName())) {
            throw new DuplicateScimUserException("a user with userName '" + input.userName() + "' already exists");
        }
        ScimUser user = new ScimUser(UUID.randomUUID(), tenantId, input.userName());
        user.setExternalId(input.externalId());
        user.setEmail(input.email());
        user.setGivenName(input.givenName());
        user.setFamilyName(input.familyName());
        user.setActive(input.active());

        // Forward to identity-service. A failure must abort the create (500) so the upstream retries,
        // rather than leaving a user in SCIM that never reached the identity store.
        try {
            String aegisUserId = identityClient.provision(tenantId,
                    StringUtils.hasText(input.email()) ? input.email() : input.userName() + "@scim.local",
                    input.userName());
            user.setAegisUserId(aegisUserId);
        } catch (Exception ex) {
            throw new ProvisioningFailedException("failed to provision user to identity-service", ex);
        }
        return users.save(user);
    }

    @Transactional(readOnly = true)
    public ScimUser get(String tenantId, UUID id) {
        return load(tenantId, id);
    }

    @Transactional(readOnly = true)
    public List<ScimUser> findByUserName(String tenantId, String userName) {
        requireTenant(tenantId);
        return users.findByTenantIdAndUserNameOrderByCreatedAt(tenantId, userName);
    }

    @Transactional(readOnly = true)
    public List<ScimUser> list(String tenantId) {
        requireTenant(tenantId);
        return users.findByTenantIdOrderByCreatedAt(tenantId);
    }

    /** Full replace (PUT). userName may not change to a value already taken by another user. */
    @Transactional
    public ScimUser replace(String tenantId, UUID id, UserInput input) {
        ScimUser user = load(tenantId, id);
        if (StringUtils.hasText(input.userName()) && !user.getUserName().equals(input.userName())) {
            if (users.existsByTenantIdAndUserName(tenantId, input.userName())) {
                throw new DuplicateScimUserException(
                        "a user with userName '" + input.userName() + "' already exists");
            }
            user.setUserName(input.userName());
        }
        user.setExternalId(input.externalId());
        user.setEmail(input.email());
        user.setGivenName(input.givenName());
        user.setFamilyName(input.familyName());
        applyActive(user, input.active());
        user.touch();
        return users.save(user);
    }

    /** PATCH: apply a single {@code active} state change (activate/deactivate). */
    @Transactional
    public ScimUser setActive(String tenantId, UUID id, boolean active) {
        ScimUser user = load(tenantId, id);
        applyActive(user, active);
        user.touch();
        return users.save(user);
    }

    /** DELETE: deactivate in identity-service, then remove the local record. */
    @Transactional
    public void delete(String tenantId, UUID id) {
        ScimUser user = load(tenantId, id);
        if (user.getAegisUserId() != null) {
            identityClient.disable(user.getAegisUserId());
        }
        users.delete(user);
    }

    // --- internals ---

    private void applyActive(ScimUser user, boolean active) {
        if (user.isActive() == active) {
            user.setActive(active);
            return;
        }
        user.setActive(active);
        if (user.getAegisUserId() != null) {
            if (active) {
                identityClient.enable(user.getAegisUserId());
            } else {
                identityClient.disable(user.getAegisUserId());
            }
        } else {
            log.warn("SCIM user {} has no aegisUserId; skipping identity-service {}",
                    user.getId(), active ? "enable" : "disable");
        }
    }

    private ScimUser load(String tenantId, UUID id) {
        requireTenant(tenantId);
        return users.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ScimUserNotFoundException("no such user in tenant"));
    }

    private static void requireTenant(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            throw new IllegalArgumentException("tenantId is required");
        }
    }
}

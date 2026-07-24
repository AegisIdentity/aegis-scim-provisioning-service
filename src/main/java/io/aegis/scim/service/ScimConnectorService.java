package io.aegis.scim.service;

import io.aegis.scim.domain.ScimConnector;
import io.aegis.scim.domain.ScimConnectorRepository;
import io.aegis.scim.service.ScimExceptions.ScimUserNotFoundException;
import io.aegis.scim.web.ConnectorDtos.ConnectorView;
import io.aegis.scim.web.ConnectorDtos.CreatedConnector;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Manages per-tenant SCIM connectors. Every operation is scoped to a tenant (taken from the caller's
 * token, never the body). A connector's opaque bearer token is generated at creation, returned once,
 * and only its SHA-256 hash is stored — so the raw secret can never be read back.
 */
@Service
public class ScimConnectorService {

    /** Path suffix the upstream appends to the gateway base to reach the SCIM endpoints. */
    static final String SCIM_BASE_URL_PATH = "/scim/v2";

    /** How long a rotated-out token keeps authenticating so the upstream can be reconfigured (M-svc-4). */
    static final Duration ROTATION_GRACE = Duration.ofHours(24);

    private final ScimConnectorRepository connectors;

    public ScimConnectorService(ScimConnectorRepository connectors) {
        this.connectors = connectors;
    }

    @Transactional(readOnly = true)
    public List<ConnectorView> list(String tenantId) {
        requireTenant(tenantId);
        return connectors.findByTenantIdOrderByCreatedAt(tenantId).stream().map(this::toView).toList();
    }

    @Transactional
    public CreatedConnector create(String tenantId, String name, Integer expiresInDays) {
        requireTenant(tenantId);
        String rawToken = TokenHasher.newToken();
        ScimConnector connector = new ScimConnector(
                UUID.randomUUID(), tenantId, name, TokenHasher.sha256Hex(rawToken));
        if (expiresInDays != null && expiresInDays > 0) {
            connector.setExpiresAt(Instant.now().plus(expiresInDays, ChronoUnit.DAYS));
        }
        ScimConnector saved = connectors.save(connector);
        return new CreatedConnector(saved.getId().toString(), saved.getName(), saved.isEnabled(),
                rawToken, SCIM_BASE_URL_PATH);
    }

    /**
     * Rotate a connector's token (M-svc-4): issue a fresh raw token (returned once), while the current
     * token stays valid for {@link #ROTATION_GRACE} so the upstream can be reconfigured without downtime.
     */
    @Transactional
    public CreatedConnector rotate(String tenantId, UUID id) {
        requireTenant(tenantId);
        ScimConnector connector = connectors.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ScimUserNotFoundException("no such connector in tenant"));
        String rawToken = TokenHasher.newToken();
        connector.rotate(TokenHasher.sha256Hex(rawToken), Instant.now().plus(ROTATION_GRACE));
        ScimConnector saved = connectors.save(connector);
        return new CreatedConnector(saved.getId().toString(), saved.getName(), saved.isEnabled(),
                rawToken, SCIM_BASE_URL_PATH);
    }

    @Transactional
    public void delete(String tenantId, UUID id) {
        requireTenant(tenantId);
        ScimConnector connector = connectors.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ScimUserNotFoundException("no such connector in tenant"));
        connectors.delete(connector);
    }

    private ConnectorView toView(ScimConnector c) {
        return new ConnectorView(c.getId().toString(), c.getName(), c.isEnabled(), c.getCreatedAt(),
                c.getExpiresAt(), c.getLastUsedAt());
    }

    private static void requireTenant(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            throw new IllegalArgumentException("tenantId is required");
        }
    }
}

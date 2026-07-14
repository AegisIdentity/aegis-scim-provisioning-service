package io.aegis.scim.service;

import io.aegis.scim.domain.ScimConnector;
import io.aegis.scim.domain.ScimConnectorRepository;
import io.aegis.scim.service.ScimExceptions.ScimUserNotFoundException;
import io.aegis.scim.web.ConnectorDtos.ConnectorView;
import io.aegis.scim.web.ConnectorDtos.CreatedConnector;
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
    public CreatedConnector create(String tenantId, String name) {
        requireTenant(tenantId);
        String rawToken = TokenHasher.newToken();
        ScimConnector connector = new ScimConnector(
                UUID.randomUUID(), tenantId, name, TokenHasher.sha256Hex(rawToken));
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
        return new ConnectorView(c.getId().toString(), c.getName(), c.isEnabled(), c.getCreatedAt());
    }

    private static void requireTenant(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            throw new IllegalArgumentException("tenantId is required");
        }
    }
}

package io.aegis.scim.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant-scoped persistence for {@link ScimConnector}. Lookup by tokenHash resolves the tenant for a
 * SCIM request; no bare findById is exposed to admin callers (they load within their own tenant). */
public interface ScimConnectorRepository extends JpaRepository<ScimConnector, UUID> {

    List<ScimConnector> findByTenantIdOrderByCreatedAt(String tenantId);

    Optional<ScimConnector> findByTenantIdAndId(String tenantId, UUID id);

    /** Resolves the connector (and thus the tenant) presenting a SCIM bearer token. */
    Optional<ScimConnector> findByTokenHash(String tokenHash);
}

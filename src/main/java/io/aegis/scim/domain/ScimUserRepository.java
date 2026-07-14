package io.aegis.scim.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant-scoped persistence for {@link ScimUser}. Every finder carries a tenant — no bare findById. */
public interface ScimUserRepository extends JpaRepository<ScimUser, UUID> {

    List<ScimUser> findByTenantIdOrderByCreatedAt(String tenantId);

    Optional<ScimUser> findByTenantIdAndId(String tenantId, UUID id);

    Optional<ScimUser> findByTenantIdAndUserName(String tenantId, String userName);

    List<ScimUser> findByTenantIdAndUserNameOrderByCreatedAt(String tenantId, String userName);

    boolean existsByTenantIdAndUserName(String tenantId, String userName);
}

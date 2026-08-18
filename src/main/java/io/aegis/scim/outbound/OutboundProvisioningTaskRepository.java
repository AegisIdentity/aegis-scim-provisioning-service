package io.aegis.scim.outbound;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for outbound provisioning tasks. */
public interface OutboundProvisioningTaskRepository extends JpaRepository<OutboundProvisioningTask, UUID> {

    Optional<OutboundProvisioningTask> findByAegisUserId(String aegisUserId);

    List<OutboundProvisioningTask> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    boolean existsByAegisUserId(String aegisUserId);
}

package io.aegis.scim.web;

import io.aegis.scim.service.ScimConnectorService;
import io.aegis.scim.web.ConnectorDtos.ConnectorView;
import io.aegis.scim.web.ConnectorDtos.CreateConnectorRequest;
import io.aegis.scim.web.ConnectorDtos.CreatedConnector;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Per-tenant SCIM connector registry. Secured by {@code SCOPE_tenant:admin} (see SecurityConfig); the
 * tenant is taken from the caller's token, so an admin manages only their own organization's
 * connectors. The connector bearer token is returned ONCE at creation and never again (only its hash
 * is stored) — GET never returns it.
 */
@RestController
public class ConnectorController {

    private final ScimConnectorService service;

    public ConnectorController(ScimConnectorService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/provisioning/connectors")
    public List<ConnectorView> list(@AuthenticationPrincipal Jwt caller) {
        return service.list(tenantOf(caller));
    }

    @PostMapping("/api/v1/provisioning/connectors")
    public ResponseEntity<CreatedConnector> create(@Valid @RequestBody CreateConnectorRequest request,
                                                   @AuthenticationPrincipal Jwt caller) {
        CreatedConnector created = service.create(tenantOf(caller), request.name(), request.expiresInDays());
        return ResponseEntity
                .created(URI.create("/api/v1/provisioning/connectors/" + created.id()))
                .body(created);
    }

    /**
     * Rotate a connector's token (M-svc-4): returns a new raw token ONCE; the old token keeps working for
     * a short grace window so the upstream can be reconfigured without downtime.
     */
    @PostMapping("/api/v1/provisioning/connectors/{id}/rotate")
    public CreatedConnector rotate(@PathVariable UUID id, @AuthenticationPrincipal Jwt caller) {
        return service.rotate(tenantOf(caller), id);
    }

    @DeleteMapping("/api/v1/provisioning/connectors/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt caller) {
        service.delete(tenantOf(caller), id);
        return ResponseEntity.noContent().build();
    }

    private static String tenantOf(Jwt caller) {
        String tenant = caller.getClaimAsString("tenant");
        if (tenant == null || tenant.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "token carries no tenant");
        }
        return tenant;
    }
}

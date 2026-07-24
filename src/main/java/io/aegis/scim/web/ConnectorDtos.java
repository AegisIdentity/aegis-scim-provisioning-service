package io.aegis.scim.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Payloads for the connector admin API. The raw bearer token is returned only once, at creation. */
public final class ConnectorDtos {

    private ConnectorDtos() {
    }

    /**
     * A connector as listed to the admin — the token is NEVER included here. {@code expiresAt} is null
     * for a non-expiring connector; {@code lastUsedAt} is null until the connector has authenticated a
     * request (M-svc-4).
     */
    public record ConnectorView(String id, String name, boolean enabled, Instant createdAt,
                                Instant expiresAt, Instant lastUsedAt) {
    }

    /** Create a connector. {@code expiresInDays} is optional; when set the token expires after that many days. */
    public record CreateConnectorRequest(@NotBlank @Size(max = 128) String name,
                                         @Positive Integer expiresInDays) {
    }

    /**
     * The one-time creation response: carries the raw {@code token} the upstream IdP is configured with
     * (never retrievable again) plus the {@code scimBaseUrlPath} to give the upstream. Only the token's
     * SHA-256 hash is stored server-side.
     */
    public record CreatedConnector(String id, String name, boolean enabled, String token,
                                   String scimBaseUrlPath) {
    }
}

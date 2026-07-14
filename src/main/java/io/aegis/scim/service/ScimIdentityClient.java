package io.aegis.scim.service;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Forwards SCIM provisioning to identity-service (the credential store). A created SCIM user is
 * find-or-created there via {@code POST /api/v1/users:provision}; deactivation/reactivation call the
 * user's {@code disable}/{@code enable} endpoints. Authenticated with a {@code client_credentials}
 * token from the authorization-server ({@link AsClientCredentialsTokenProvider}).
 */
@Component
public class ScimIdentityClient {

    private static final Logger log = LoggerFactory.getLogger(ScimIdentityClient.class);

    private final AsClientCredentialsTokenProvider tokenProvider;
    private final RestClient restClient;

    public ScimIdentityClient(AsClientCredentialsTokenProvider tokenProvider,
                              @Value("${aegis.identity-service.base-url:http://localhost:9102}") String baseUrl) {
        this.tokenProvider = tokenProvider;
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    /**
     * Provisions (find-or-create by email) the user in identity-service and returns the Aegis user id.
     * Throws on any failure — a SCIM create must fail (500) so the upstream retries rather than leaving
     * a user that exists in SCIM but not in the identity store.
     */
    public String provision(String tenantId, String email, String username) {
        ProvisionResult result = restClient.post()
                .uri("/api/v1/users:provision")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("tenantId", tenantId, "email", email,
                        "username", username == null ? "" : username))
                .retrieve()
                .body(ProvisionResult.class);
        if (result == null || result.id() == null) {
            throw new IllegalStateException("identity-service returned no user for provisioning");
        }
        return result.id();
    }

    /** Disables the user in identity-service. Best-effort: a failure is logged, not fatal to the SCIM op. */
    public void disable(String aegisUserId) {
        setStatus(aegisUserId, "disable");
    }

    /** Enables the user in identity-service. Best-effort: a failure is logged, not fatal to the SCIM op. */
    public void enable(String aegisUserId) {
        setStatus(aegisUserId, "enable");
    }

    private void setStatus(String aegisUserId, String action) {
        if (aegisUserId == null || aegisUserId.isBlank()) {
            return;
        }
        try {
            restClient.post()
                    .uri("/api/v1/users/{id}/{action}", aegisUserId, action)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.token())
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("identity-service {} failed for aegisUserId={}: {}", action, aegisUserId, ex.toString());
        }
    }

    private record ProvisionResult(String id, String tenantId, String username, String email, String status) {
    }
}

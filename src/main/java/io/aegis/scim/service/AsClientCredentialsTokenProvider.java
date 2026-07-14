package io.aegis.scim.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Obtains (and caches) an access token from the authorization-server token endpoint via the
 * {@code client_credentials} grant, so this service can call identity-service as a trusted internal
 * caller. Authenticates with HTTP Basic (client id + secret) and requests the identity provisioning
 * scopes. The token is cached until shortly before it expires and re-fetched lazily on demand.
 */
@Component
public class AsClientCredentialsTokenProvider {

    /** Scopes needed to provision + enable/disable users in identity-service. */
    static final String SCOPES = "identity:users:provision identity:users:write";

    private final RestClient tokenClient;
    private final String clientId;
    private final String clientSecret;

    private volatile String cachedToken;
    private volatile Instant cachedExpiry = Instant.EPOCH;

    public AsClientCredentialsTokenProvider(
            @Value("${aegis.as.token-uri:http://localhost:9000/oauth2/token}") String tokenUri,
            @Value("${aegis.scim.client-id:aegis-scim}") String clientId,
            @Value("${aegis.scim.client-secret:dev-only-change-me}") String clientSecret) {
        this.tokenClient = RestClient.builder().baseUrl(tokenUri).build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public synchronized String token() {
        Instant now = Instant.now();
        if (cachedToken != null && cachedExpiry.isAfter(now.plusSeconds(30))) {
            return cachedToken;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("scope", SCOPES);

        TokenResponse response = tokenClient.post()
                .headers(h -> h.setBasicAuth(clientId, clientSecret))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
        if (response == null || response.accessToken() == null) {
            throw new IllegalStateException("authorization-server returned no access token");
        }
        long ttl = response.expiresIn() > 0 ? response.expiresIn() : 300L;
        cachedToken = response.accessToken();
        cachedExpiry = now.plus(Duration.ofSeconds(ttl));
        return cachedToken;
    }

    /** The token endpoint response; only the two fields we use are bound. */
    record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn) {
    }
}

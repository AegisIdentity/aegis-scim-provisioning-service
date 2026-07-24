package io.aegis.scim.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegis.scim.domain.ScimConnector;
import io.aegis.scim.domain.ScimConnectorRepository;
import io.aegis.scim.service.TokenHasher;
import io.aegis.scim.web.ScimDtos.ScimError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates SCIM requests (paths under {@code /scim/v2/**}) with the per-connector opaque bearer
 * token — NOT the JWT resource-server chain. Reads {@code Authorization: Bearer <token>}, hashes it,
 * and looks up an enabled {@link ScimConnector} by {@code tokenHash}; on a match it stashes the
 * resolved tenant (and connector id) as request attributes for the controller and sets a
 * {@code SCIM_CONNECTOR} authentication in the security context. On no/invalid token it writes a SCIM
 * 401 Error and stops the chain, so a SCIM endpoint is never reached unauthenticated.
 *
 * <p>The security config permits {@code /scim/v2/**} so the JWT authorization rules do not reject
 * these requests; this filter (wired into the security chain before the JWT bearer filter) enforces
 * the connector token. On success it strips the {@code Authorization} header from the downstream
 * request so the JWT {@code BearerTokenAuthenticationFilter} does not try to decode the opaque
 * connector token as a JWT (which would 401).
 */
public class ScimConnectorAuthFilter extends OncePerRequestFilter {

    /** Request attribute the SCIM controller reads the resolved tenant from. */
    public static final String TENANT_ATTRIBUTE = ScimConnectorAuthFilter.class.getName() + ".tenant";
    /** Request attribute carrying the authenticated connector id. */
    public static final String CONNECTOR_ID_ATTRIBUTE = ScimConnectorAuthFilter.class.getName() + ".connectorId";

    private static final String BEARER_PREFIX = "Bearer ";

    private final ScimConnectorRepository connectors;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ScimConnectorAuthFilter(ScimConnectorRepository connectors) {
        this.connectors = connectors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/scim/v2");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            writeUnauthorized(response, "missing or malformed bearer token");
            return;
        }
        String rawToken = header.substring(BEARER_PREFIX.length()).trim();
        if (rawToken.isEmpty()) {
            writeUnauthorized(response, "missing bearer token");
            return;
        }
        String presentedHash = TokenHasher.sha256Hex(rawToken);
        Optional<ScimConnector> connector =
                connectors.findByTokenHashOrPreviousTokenHash(presentedHash, presentedHash)
                        .filter(ScimConnector::isEnabled);
        if (connector.isEmpty()) {
            writeUnauthorized(response, "invalid connector token");
            return;
        }
        ScimConnector c = connector.get();
        Instant now = Instant.now();

        // M-svc-5: compare hashes constant-time (defense-in-depth) to decide which slot matched.
        boolean currentMatch = constantTimeHexEquals(presentedHash, c.getTokenHash());
        boolean graceMatch = !currentMatch
                && constantTimeHexEquals(presentedHash, c.getPreviousTokenHash())
                && c.isPreviousTokenValid(now);
        if (!currentMatch && !graceMatch) {
            // Matched only a previous token whose grace window has closed (or a spurious index hit).
            writeUnauthorized(response, "invalid connector token");
            return;
        }
        // M-svc-4: enforce absolute token expiry.
        if (c.isExpired(now)) {
            writeUnauthorized(response, "connector token expired");
            return;
        }
        // M-svc-4: last-used-at tracking.
        c.markUsed(now);
        connectors.save(c);

        request.setAttribute(TENANT_ATTRIBUTE, c.getTenantId());
        request.setAttribute(CONNECTOR_ID_ATTRIBUTE, c.getId().toString());

        var authentication = new UsernamePasswordAuthenticationToken(
                "scim-connector:" + c.getId(), null,
                List.of(new SimpleGrantedAuthority("SCIM_CONNECTOR")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            // Hide the Authorization header so the JWT bearer filter does not try to decode the opaque
            // connector token as a JWT.
            chain.doFilter(new StrippedAuthorizationRequest(request), response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /** Constant-time comparison of two hex token hashes (M-svc-5). Null-safe: null never matches. */
    private static boolean constantTimeHexEquals(String presentedHash, String storedHash) {
        if (presentedHash == null || storedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                presentedHash.getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
    }

    private void writeUnauthorized(HttpServletResponse response, String detail) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                ScimError.of(HttpStatus.UNAUTHORIZED.value(), detail)));
    }

    /** A request view with the {@code Authorization} header removed, so downstream JWT auth is skipped. */
    private static final class StrippedAuthorizationRequest extends HttpServletRequestWrapper {
        StrippedAuthorizationRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getHeader(String name) {
            return HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name) ? null : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)
                    ? Collections.emptyEnumeration() : super.getHeaders(name);
        }
    }
}

package io.aegis.scim;

import io.aegis.commons.security.SecurityHardening;
import io.aegis.scim.domain.ScimConnectorRepository;
import io.aegis.scim.web.ScimConnectorAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Resource-server security baseline (default-deny, shared hardening headers, 401-not-302).
 *
 * <p>Two authentication surfaces coexist:
 * <ul>
 *   <li>{@code /api/v1/provisioning/**} — the connector admin API, gated {@code SCOPE_tenant:admin} on
 *       the JWT resource-server chain (tenant from the token).</li>
 *   <li>{@code /scim/v2/**} — the SCIM endpoints, {@code permitAll} HERE so the JWT filter does not
 *       reject them; they are authenticated instead by {@code ScimConnectorAuthFilter} using the
 *       per-connector opaque bearer token, which resolves the tenant. The SCIM surface is never open:
 *       that filter 401s any request without a valid connector token.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http,
                                                      ScimConnectorRepository connectors) throws Exception {
        SecurityHardening.applyHardeningHeaders(http);
        SecurityHardening.statelessBearerApi(http);
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // SCIM endpoints are authenticated by the connector-token filter, not the JWT
                        // chain. permitAll keeps the JWT filter from rejecting them; the filter enforces.
                        .requestMatchers("/scim/v2/**").permitAll()
                        // Connector admin API: per-tenant, tenant from the JWT.
                        .requestMatchers("/api/v1/provisioning/**").hasAuthority("SCOPE_tenant:admin")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                // Enforce the per-connector bearer token on /scim/v2/** BEFORE the JWT bearer filter
                // runs, so those requests are authenticated by the connector token, not a JWT.
                .addFilterBefore(new ScimConnectorAuthFilter(connectors),
                        BearerTokenAuthenticationFilter.class);
        return http.build();
    }
}

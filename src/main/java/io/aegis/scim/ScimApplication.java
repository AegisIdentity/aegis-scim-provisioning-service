package io.aegis.scim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** SCIM 2.0 inbound provisioning provider.
 *
 * <p>An upstream IdP (Entra/Okta/Workday) pushes Users into a tenant over SCIM, authenticated by a
 * per-connector opaque bearer token. Provisioned users are stored locally AND forwarded to
 * identity-service. Two surfaces: a JWT-secured connector admin API ({@code /api/v1/provisioning/**})
 * and the connector-token-secured SCIM endpoints ({@code /scim/v2/**}). */
@SpringBootApplication
public class ScimApplication {
    public static void main(String[] args) {
        SpringApplication.run(ScimApplication.class, args);
    }
}

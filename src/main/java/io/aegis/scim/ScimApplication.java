package io.aegis.scim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** SCIM 2.0 inbound and outbound user/group provisioning.
 *
 * <p>Maturity: scaffold. This is a buildable, secured resource-server skeleton (health + a
 * protected info endpoint + the shared hardening baseline) ready for feature work. See
 * aegis-platform-docs/architecture/SERVICE-CATALOG.md for the intended contract. */
@SpringBootApplication
public class ScimApplication {
    public static void main(String[] args) {
        SpringApplication.run(ScimApplication.class, args);
    }
}

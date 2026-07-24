package io.aegis.scim.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fail-fast guard for dev-default secrets (L-svc-1). When external secrets are required
 * ({@code prod}/{@code stage} profile, or {@code aegis.security.require-external-secrets=true}), context
 * creation aborts if any sensitive property is unset or still holds the well-known
 * {@code dev-only-change-me} placeholder. Covers both the DB password and the SCIM client_credentials
 * secret used to authenticate to identity-service. In dev/test the dev defaults are allowed so the
 * service runs out-of-the-box.
 */
@Component
public class StartupSecretsGuard {

    static final String DEV_SENTINEL = "dev-only-change-me";

    public StartupSecretsGuard(Environment env) {
        if (!SecretsPolicy.externalSecretsRequired(env)) {
            return;
        }
        List<String> offenders = new ArrayList<>();
        checkSecret(env, "spring.datasource.password", offenders);
        checkSecret(env, "aegis.scim.client-secret", offenders);
        if (!offenders.isEmpty()) {
            throw new IllegalStateException("Refusing to start: dev-default or missing secrets in a "
                    + "prod/stage deployment — set " + offenders);
        }
    }

    static void checkSecret(Environment env, String property, List<String> offenders) {
        String value = env.getProperty(property);
        if (value == null || value.isBlank() || DEV_SENTINEL.equals(value)) {
            offenders.add(property);
        }
    }
}

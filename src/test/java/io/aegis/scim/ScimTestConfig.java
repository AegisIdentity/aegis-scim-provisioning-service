package io.aegis.scim;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Test infrastructure: a real Postgres. The real {@code JwtDecoder} ({@code ResourceServerJwtConfig})
 * is used as-is — it builds lazily (no network at startup) and is never invoked, since the {@code jwt()}
 * MockMvc mutator injects a pre-authenticated token directly into the security context. The
 * {@code ScimIdentityClient} is mocked in the tests (via {@code @MockitoBean}) so no real
 * identity-service is needed.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ScimTestConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("aegis_scim");
    }
}

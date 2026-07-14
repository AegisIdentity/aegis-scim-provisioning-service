package io.aegis.scim;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.aegis.scim.service.ScimIdentityClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Security baseline: health is open, the connector admin API is default-deny (401 without a token),
 * and the SCIM surface is closed to requests without a valid connector bearer token (401).
 */
@SpringBootTest
@Import(ScimTestConfig.class)
class ScimSecurityTest {

    @Autowired
    WebApplicationContext context;
    @MockitoBean
    ScimIdentityClient identityClient;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void health_is_public() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void connector_admin_requires_a_token() throws Exception {
        mockMvc.perform(get("/api/v1/provisioning/connectors")).andExpect(status().isUnauthorized());
    }

    @Test
    void scim_requires_a_connector_bearer_token() throws Exception {
        mockMvc.perform(get("/scim/v2/Users")).andExpect(status().isUnauthorized());
    }
}

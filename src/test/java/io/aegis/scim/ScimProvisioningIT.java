package io.aegis.scim;

import static io.aegis.commons.testing.AegisJwtTest.jwtForTenant;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.aegis.scim.service.ScimIdentityClient;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * End-to-end SCIM provisioning tests against a real Postgres. Covers the connector admin API
 * (scope-gated, token returned once, never re-listed) and the full SCIM lifecycle authenticated by the
 * connector bearer token (create/get/filter/patch/delete), plus tenant isolation. The forward to
 * identity-service is mocked, so no real identity-service is needed.
 */
@SpringBootTest
@Import(ScimTestConfig.class)
class ScimProvisioningIT {

    @Autowired
    WebApplicationContext context;
    @MockitoBean
    ScimIdentityClient identityClient;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(identityClient.provision(anyString(), any(), any())).thenReturn(UUID.randomUUID().toString());
    }

    // --- connector admin API ---

    @Test
    void creating_a_connector_requires_a_token() throws Exception {
        mockMvc.perform(post("/api/v1/provisioning/connectors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Entra\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void creating_a_connector_requires_tenant_admin_scope() throws Exception {
        // Authenticated but without tenant:admin -> 403.
        mockMvc.perform(post("/api/v1/provisioning/connectors")
                        .with(jwtForTenant("acme", "u-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Entra\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_connector_returns_the_raw_token_once_and_never_lists_it() throws Exception {
        String body = mockMvc.perform(post("/api/v1/provisioning/connectors")
                        .with(jwtForTenant("acme", "admin", "tenant:admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Entra\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Entra"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.scimBaseUrlPath").value("/scim/v2"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");

        // The list view never includes the token.
        mockMvc.perform(get("/api/v1/provisioning/connectors")
                        .with(jwtForTenant("acme", "admin", "tenant:admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + id + "')].name").value("Entra"))
                .andExpect(jsonPath("$[0].token").doesNotExist());
    }

    @Test
    void a_connector_can_be_deleted() throws Exception {
        String token = createConnector("acme", "Okta");
        String id = connectorIdFor("acme");
        mockMvc.perform(delete("/api/v1/provisioning/connectors/" + id)
                        .with(jwtForTenant("acme", "admin", "tenant:admin")))
                .andExpect(status().isNoContent());
        // Its SCIM token no longer authenticates.
        mockMvc.perform(get("/scim/v2/Users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    // --- SCIM endpoints (connector token) ---

    @Test
    void rotating_a_connector_issues_a_new_token_and_keeps_the_old_valid_during_grace() throws Exception {
        String oldToken = createConnector("rotate-co", "Entra");
        String id = connectorIdFor("rotate-co");

        // The old token authenticates before rotation.
        mockMvc.perform(get("/scim/v2/Users").header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken))
                .andExpect(status().isOk());

        // Rotate -> a NEW raw token is returned once, different from the old one.
        String rotateBody = mockMvc.perform(post("/api/v1/provisioning/connectors/" + id + "/rotate")
                        .with(jwtForTenant("rotate-co", "admin", "tenant:admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String newToken = JsonPath.read(rotateBody, "$.token");
        org.assertj.core.api.Assertions.assertThat(newToken).isNotEqualTo(oldToken);

        // The new token authenticates.
        mockMvc.perform(get("/scim/v2/Users").header(HttpHeaders.AUTHORIZATION, "Bearer " + newToken))
                .andExpect(status().isOk());
        // And the old token still authenticates during the grace window (no upstream downtime).
        mockMvc.perform(get("/scim/v2/Users").header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken))
                .andExpect(status().isOk());
    }

    @Test
    void scim_rejects_a_bad_bearer_token() throws Exception {
        mockMvc.perform(get("/scim/v2/Users").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.schemas[0]").value("urn:ietf:params:scim:api:messages:2.0:Error"));
    }

    @Test
    void scim_user_create_get_filter_patch_delete_lifecycle() throws Exception {
        String token = createConnector("lifecycle", "Entra");

        // Create.
        String created = mockMvc.perform(post("/scim/v2/Users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType("application/scim+json")
                        .content("{"
                                + "\"schemas\":[\"urn:ietf:params:scim:schemas:core:2.0:User\"],"
                                + "\"userName\":\"jdoe@acme.com\","
                                + "\"externalId\":\"ext-1\","
                                + "\"name\":{\"givenName\":\"Jane\",\"familyName\":\"Doe\"},"
                                + "\"emails\":[{\"value\":\"jane@acme.com\",\"primary\":true}],"
                                + "\"active\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.userName").value("jdoe@acme.com"))
                .andExpect(jsonPath("$.meta.resourceType").value("User"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");

        // Get by id.
        mockMvc.perform(get("/scim/v2/Users/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.userName").value("jdoe@acme.com"));

        // Filter by userName (the standard IdP lookup).
        mockMvc.perform(get("/scim/v2/Users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("filter", "userName eq \"jdoe@acme.com\"")
                        .param("startIndex", "1").param("count", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemas[0]").value("urn:ietf:params:scim:api:messages:2.0:ListResponse"))
                .andExpect(jsonPath("$.totalResults").value(1))
                .andExpect(jsonPath("$.Resources[0].userName").value("jdoe@acme.com"));

        // A filter that matches nothing returns an empty ListResponse (not a 404).
        mockMvc.perform(get("/scim/v2/Users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("filter", "userName eq \"nobody@acme.com\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalResults").value(0));

        // PATCH active=false -> deactivated.
        mockMvc.perform(patch("/scim/v2/Users/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType("application/scim+json")
                        .content("{"
                                + "\"schemas\":[\"urn:ietf:params:scim:api:messages:2.0:PatchOp\"],"
                                + "\"Operations\":[{\"op\":\"replace\",\"path\":\"active\",\"value\":false}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        // DELETE -> 204, then 404 on subsequent GET.
        mockMvc.perform(delete("/scim/v2/Users/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/scim/v2/Users/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.schemas[0]").value("urn:ietf:params:scim:api:messages:2.0:Error"));
    }

    @Test
    void scim_get_unknown_user_is_a_scim_404() throws Exception {
        String token = createConnector("missing", "Okta");
        mockMvc.perform(get("/scim/v2/Users/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.schemas[0]").value("urn:ietf:params:scim:api:messages:2.0:Error"))
                .andExpect(jsonPath("$.status").value("404"));
    }

    @Test
    void scim_users_are_tenant_isolated() throws Exception {
        String tokenA = createConnector("tenant-a", "Entra");
        String tokenB = createConnector("tenant-b", "Entra");

        // Create a user in tenant A.
        String created = mockMvc.perform(post("/scim/v2/Users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userName\":\"shared@x.com\",\"active\":true}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String idInA = JsonPath.read(created, "$.id");

        // Tenant B's connector token cannot see tenant A's user (404).
        mockMvc.perform(get("/scim/v2/Users/" + idInA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // And tenant B's unfiltered list does not include it.
        mockMvc.perform(get("/scim/v2/Users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalResults").value(0));

        // Tenant A can see it via its own filter.
        mockMvc.perform(get("/scim/v2/Users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .param("filter", "userName eq \"shared@x.com\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalResults").value(1));
    }

    @Test
    void service_provider_config_advertises_patch_and_filter() throws Exception {
        String token = createConnector("spc", "Okta");
        mockMvc.perform(get("/scim/v2/ServiceProviderConfig")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patch.supported").value(true))
                .andExpect(jsonPath("$.filter.supported").value(true))
                .andExpect(jsonPath("$.bulk.supported").value(false))
                .andExpect(jsonPath("$.etag.supported").value(false));
    }

    // --- helpers ---

    /** Creates a connector for the tenant and returns its raw SCIM bearer token. */
    private String createConnector(String tenant, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/provisioning/connectors")
                        .with(jwtForTenant(tenant, "admin", "tenant:admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private String connectorIdFor(String tenant) throws Exception {
        String body = mockMvc.perform(get("/api/v1/provisioning/connectors")
                        .with(jwtForTenant(tenant, "admin", "tenant:admin")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$[0].id");
    }
}

package io.aegis.scim.web;

import io.aegis.scim.web.ScimDtos.ScimUserResource;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Static SCIM 2.0 discovery metadata (RFC 7643 §5, §6): ServiceProviderConfig, ResourceTypes, Schemas.
 * Advertises what this provider supports — PATCH yes, filter yes, no bulk, no ETag, no change password
 * or sort. Still under {@code /scim/v2/**}, so authenticated by the connector filter like the rest.
 */
@RestController
@RequestMapping(path = "/scim/v2",
        produces = {"application/scim+json", MediaType.APPLICATION_JSON_VALUE})
public class ScimMetadataController {

    @GetMapping("/ServiceProviderConfig")
    public Map<String, Object> serviceProviderConfig() {
        return Map.of(
                "schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig"),
                "documentationUri", "https://datatracker.ietf.org/doc/html/rfc7644",
                "patch", Map.of("supported", true),
                "bulk", Map.of("supported", false, "maxOperations", 0, "maxPayloadSize", 0),
                "filter", Map.of("supported", true, "maxResults", 200),
                "changePassword", Map.of("supported", false),
                "sort", Map.of("supported", false),
                "etag", Map.of("supported", false),
                "authenticationSchemes", List.of(Map.of(
                        "type", "oauthbearertoken",
                        "name", "OAuth Bearer Token",
                        "description", "Per-connector opaque bearer token",
                        "primary", true)));
    }

    @GetMapping("/ResourceTypes")
    public List<Map<String, Object>> resourceTypes() {
        return List.of(Map.of(
                "schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:ResourceType"),
                "id", "User",
                "name", "User",
                "endpoint", "/Users",
                "description", "User Account",
                "schema", ScimDtos.USER_SCHEMA,
                "meta", Map.of("resourceType", "ResourceType", "location", "/scim/v2/ResourceTypes/User")));
    }

    @GetMapping("/Schemas")
    public List<Map<String, Object>> schemas() {
        return List.of(Map.of(
                "id", ScimDtos.USER_SCHEMA,
                "name", "User",
                "description", "User Account",
                "attributes", List.of(
                        attr("userName", "string", true, "server"),
                        attr("externalId", "string", false, "none"),
                        attr("active", "boolean", false, "none"),
                        attr("name", "complex", false, "none"),
                        attr("emails", "complex", false, "none")),
                "meta", Map.of("resourceType", "Schema", "location", "/scim/v2/Schemas/" + ScimDtos.USER_SCHEMA)));
    }

    // Keep a compile-time reference to the User resource shape so the schema stays in step with the DTO.
    @SuppressWarnings("unused")
    private static final Class<ScimUserResource> USER_SHAPE = ScimUserResource.class;

    private static Map<String, Object> attr(String name, String type, boolean required, String uniqueness) {
        return Map.of(
                "name", name,
                "type", type,
                "required", required,
                "caseExact", false,
                "mutability", "readWrite",
                "returned", "default",
                "uniqueness", uniqueness);
    }
}

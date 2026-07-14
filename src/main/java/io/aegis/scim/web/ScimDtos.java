package io.aegis.scim.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.aegis.scim.domain.ScimUser;
import java.util.List;

/**
 * SCIM 2.0 wire shapes (RFC 7643/7644). Records are (de)serialized by Jackson; the field names match
 * the SCIM attribute names. {@code JsonInclude(NON_NULL)} keeps optional attributes out when absent.
 */
public final class ScimDtos {

    public static final String USER_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:User";
    public static final String LIST_RESPONSE_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:ListResponse";
    public static final String ERROR_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:Error";
    public static final String PATCH_OP_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:PatchOp";

    private ScimDtos() {
    }

    /** SCIM {@code name} complex attribute. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Name(String givenName, String familyName, String formatted) {
    }

    /** SCIM {@code emails} multi-valued attribute entry. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Email(String value, String type, Boolean primary) {
    }

    /** SCIM {@code meta} attribute. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Meta(String resourceType, String location, String created, String lastModified) {
    }

    /**
     * A SCIM User resource, used for both request (create/replace) and response. On input, the upstream
     * sends {@code userName}, optional {@code name}, {@code emails}, {@code active}, {@code externalId};
     * unrecognized attributes are ignored ({@code @JsonIgnoreProperties} on the reader is not needed as
     * Spring's mapper ignores unknowns by default in this service).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ScimUserResource(
            List<String> schemas,
            String id,
            String externalId,
            String userName,
            Name name,
            List<Email> emails,
            Boolean active,
            Meta meta) {

        public static ScimUserResource from(ScimUser u) {
            Name name = (u.getGivenName() != null || u.getFamilyName() != null)
                    ? new Name(u.getGivenName(), u.getFamilyName(), null)
                    : null;
            List<Email> emails = u.getEmail() != null
                    ? List.of(new Email(u.getEmail(), "work", Boolean.TRUE))
                    : null;
            Meta meta = new Meta("User",
                    "/scim/v2/Users/" + u.getId(),
                    u.getCreatedAt() == null ? null : u.getCreatedAt().toString(),
                    u.getUpdatedAt() == null ? null : u.getUpdatedAt().toString());
            return new ScimUserResource(List.of(USER_SCHEMA), u.getId().toString(), u.getExternalId(),
                    u.getUserName(), name, emails, u.isActive(), meta);
        }
    }

    /** SCIM ListResponse envelope. */
    public record ListResponse(
            List<String> schemas,
            int totalResults,
            int startIndex,
            int itemsPerPage,
            @com.fasterxml.jackson.annotation.JsonProperty("Resources") List<ScimUserResource> resources) {

        public static ListResponse of(List<ScimUserResource> resources, int startIndex, int itemsPerPage,
                                      int totalResults) {
            return new ListResponse(List.of(LIST_RESPONSE_SCHEMA), totalResults, startIndex, itemsPerPage,
                    resources);
        }
    }

    /** SCIM Error response. */
    public record ScimError(List<String> schemas, String status, String detail, String scimType) {
        public static ScimError of(int status, String detail) {
            return new ScimError(List.of(ERROR_SCHEMA), String.valueOf(status), detail, null);
        }

        public static ScimError of(int status, String detail, String scimType) {
            return new ScimError(List.of(ERROR_SCHEMA), String.valueOf(status), detail, scimType);
        }
    }

    /** SCIM PatchOp request (RFC 7644 §3.5.2). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PatchOp(List<String> schemas, List<Operation> operations) {

        @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
        public record Operation(String op, String path, Object value) {
        }

        // Jackson maps the SCIM "Operations" (capital O) attribute onto this record component.
        @com.fasterxml.jackson.annotation.JsonCreator
        public PatchOp(
                @com.fasterxml.jackson.annotation.JsonProperty("schemas") List<String> schemas,
                @com.fasterxml.jackson.annotation.JsonProperty("Operations") List<Operation> operations) {
            this.schemas = schemas;
            this.operations = operations;
        }
    }
}

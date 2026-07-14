package io.aegis.scim.service;

/** Domain exceptions for SCIM provisioning, mapped to SCIM/HTTP responses by the web layer. */
public final class ScimExceptions {

    private ScimExceptions() {
    }

    /** No such SCIM user in the tenant (SCIM 404). */
    public static class ScimUserNotFoundException extends RuntimeException {
        public ScimUserNotFoundException(String message) {
            super(message);
        }
    }

    /** A userName already exists within the tenant (SCIM 409 uniqueness). */
    public static class DuplicateScimUserException extends RuntimeException {
        public DuplicateScimUserException(String message) {
            super(message);
        }
    }

    /** The SCIM request payload is missing a required field or is malformed (SCIM 400). */
    public static class ScimBadRequestException extends RuntimeException {
        public ScimBadRequestException(String message) {
            super(message);
        }
    }

    /** Forwarding to identity-service failed; a create must surface this so the upstream retries (500). */
    public static class ProvisioningFailedException extends RuntimeException {
        public ProvisioningFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

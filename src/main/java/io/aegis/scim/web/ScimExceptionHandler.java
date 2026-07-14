package io.aegis.scim.web;

import io.aegis.scim.service.ScimExceptions.DuplicateScimUserException;
import io.aegis.scim.service.ScimExceptions.ProvisioningFailedException;
import io.aegis.scim.service.ScimExceptions.ScimBadRequestException;
import io.aegis.scim.service.ScimExceptions.ScimUserNotFoundException;
import io.aegis.scim.web.ScimDtos.ScimError;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps SCIM domain exceptions to SCIM 2.0 Error responses (RFC 7644 §3.12) — scoped to the SCIM
 * controllers so the connector-facing surface always speaks SCIM, while the admin API uses RFC-7807.
 */
@RestControllerAdvice(assignableTypes = {ScimUserController.class, ScimMetadataController.class})
public class ScimExceptionHandler {

    @ExceptionHandler(ScimUserNotFoundException.class)
    public ResponseEntity<ScimError> handleNotFound(ScimUserNotFoundException ex) {
        return scim(HttpStatus.NOT_FOUND, ex.getMessage(), null);
    }

    @ExceptionHandler(DuplicateScimUserException.class)
    public ResponseEntity<ScimError> handleDuplicate(DuplicateScimUserException ex) {
        return scim(HttpStatus.CONFLICT, ex.getMessage(), "uniqueness");
    }

    @ExceptionHandler({ScimBadRequestException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ScimError> handleBadRequest(Exception ex) {
        return scim(HttpStatus.BAD_REQUEST, ex.getMessage(), "invalidValue");
    }

    @ExceptionHandler(ProvisioningFailedException.class)
    public ResponseEntity<ScimError> handleProvisioningFailed(ProvisioningFailedException ex) {
        // Surface a 500 SCIM error so the upstream IdP retries the create (standard SCIM behavior).
        return scim(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), null);
    }

    private static ResponseEntity<ScimError> scim(HttpStatus status, String detail, String scimType) {
        return ResponseEntity.status(status)
                .contentType(MediaType.valueOf("application/scim+json"))
                .body(ScimError.of(status.value(), detail, scimType));
    }
}

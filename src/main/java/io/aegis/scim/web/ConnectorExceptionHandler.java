package io.aegis.scim.web;

import io.aegis.scim.service.ScimExceptions.ScimUserNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps connector-admin domain exceptions to RFC-7807 responses (admin API surface). */
@RestControllerAdvice(assignableTypes = ConnectorController.class)
public class ConnectorExceptionHandler {

    @ExceptionHandler(ScimUserNotFoundException.class)
    public ProblemDetail handleNotFound(ScimUserNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }
}

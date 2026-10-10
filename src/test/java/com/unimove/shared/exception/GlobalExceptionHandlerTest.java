package com.unimove.shared.exception;

import org.hibernate.query.sqm.PathElementException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest req = new MockHttpServletRequest("GET", "/rides/history");

    @Test
    void unknownSortPathIsBadRequest() {
        var ex = new InvalidDataAccessApiUsageException("sort",
                new IllegalArgumentException(new PathElementException("Could not resolve attribute 'x'")));

        var resp = handler.handleInvalidSort(ex, req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().message()).isEqualTo("Parâmetro de ordenação inválido.");
    }

    @Test
    void otherInvalidUsageStaysInternalError() {
        var ex = new InvalidDataAccessApiUsageException("bug", new IllegalStateException("outra coisa"));

        assertThat(handler.handleInvalidSort(ex, req).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void unknownRouteIsNotFound() {
        var resp = handler.handleUnexpected(new NoResourceFoundException(HttpMethod.GET, "actuator/env"), req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().message()).isEqualTo("Recurso não encontrado");
    }

    @Test
    void wrongMethodIsMethodNotAllowed() {
        var resp = handler.handleUnexpected(new HttpRequestMethodNotSupportedException("DELETE"), req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void genuineFailureStaysInternalError() {
        var resp = handler.handleUnexpected(new IllegalStateException("bug"), req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().message()).isEqualTo("Erro interno do servidor");
    }
}

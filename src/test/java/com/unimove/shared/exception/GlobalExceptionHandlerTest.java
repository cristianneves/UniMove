package com.unimove.shared.exception;

import org.hibernate.query.sqm.PathElementException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

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
}

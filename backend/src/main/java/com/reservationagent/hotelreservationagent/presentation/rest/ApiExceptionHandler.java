package com.reservationagent.hotelreservationagent.presentation.rest;

import com.reservationagent.hotelreservationagent.application.service.validation.OutOfScopeLocationException;
import com.reservationagent.hotelreservationagent.application.service.validation.RequestValidationException;
import com.reservationagent.hotelreservationagent.domain.shared.DomainException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;

/**
 * RFC 9457 problem details for the API. 400 bodies carry {@code errors: [...]}; 422 bodies carry
 * {@code suggestions: [...]} (other Ontario municipalities the city could mean).
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(RequestValidationException.class)
    ProblemDetail invalidRequest(RequestValidationException e) {
        return badRequest(e.errors());
    }

    @ExceptionHandler(DomainException.class)
    ProblemDetail domainRuleViolated(DomainException e) {
        return badRequest(List.of(e.getMessage()));
    }

    @ExceptionHandler(OutOfScopeLocationException.class)
    ProblemDetail outOfScope(OutOfScopeLocationException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
        problem.setTitle(e.suggestions().isEmpty() ? "Location out of scope" : "Ambiguous location");
        problem.setProperty("suggestions", e.suggestions());
        return problem;
    }

    @ExceptionHandler(TaskNotFoundException.class)
    ProblemDetail notFound(TaskNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Task not found");
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .sorted()
                .toList();
        return ResponseEntity.badRequest().body(badRequest(errors));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        return ResponseEntity.badRequest().body(badRequest(List.of(unreadableReason(ex))));
    }

    /** "field: reason" for a bad value (wrong enum, malformed date), else a generic message. */
    private static String unreadableReason(HttpMessageNotReadableException ex) {
        JacksonException json = null;
        for (Throwable t = ex; t != null && json == null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof JacksonException j) {
                json = j;
            }
        }
        if (json != null) {
            String field = json.getPath().stream()
                    .map(r -> r.getPropertyName() != null ? r.getPropertyName() : "[" + r.getIndex() + "]")
                    .collect(Collectors.joining("."));
            if (field.isEmpty()) {
                return "Request body is not valid JSON";
            }
            Class<?> target = json instanceof MismatchedInputException m ? m.getTargetType() : null;
            String reason;
            if (target != null && target.isEnum()) {
                reason = "must be one of " + Arrays.toString(target.getEnumConstants());
            } else if (target == LocalDate.class) {
                reason = "must be a date in YYYY-MM-DD format";
            } else if (target == Instant.class) {
                reason = "must be an ISO-8601 instant, e.g. 2026-11-10T18:00:00Z";
            } else {
                reason = "has an invalid value";
            }
            return field + ": " + reason;
        }
        return "Request body is missing or is not valid JSON";
    }

    private static ProblemDetail badRequest(List<String> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, String.join("; ", errors));
        problem.setTitle("Invalid request");
        problem.setProperty("errors", errors);
        return problem;
    }
}

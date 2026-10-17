package com.reservationagent.hotelreservationagent.presentation.rest;

import com.reservationagent.hotelreservationagent.application.port.out.CallRecordingSource.RecordingUnavailableException;
import com.reservationagent.hotelreservationagent.application.port.out.TaskPromptInterpreter.InterpreterUnavailableException;
import com.reservationagent.hotelreservationagent.application.service.validation.OutOfScopeLocationException;
import com.reservationagent.hotelreservationagent.application.service.validation.RequestValidationException;
import com.reservationagent.hotelreservationagent.application.service.validation.RequestValidationException.FieldError;
import com.reservationagent.hotelreservationagent.domain.shared.DomainException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;

/**
 * RFC 9457 problem details for the API. 400 bodies carry {@code errors: [{field, message}]}, where
 * {@code field} is the request field's JSON name or {@code null} for the whole request; 422 bodies
 * carry {@code suggestions: [...]} (other municipalities the city could mean); 503 means the
 * AI provider that reads a free-text prompt is unavailable.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(RequestValidationException.class)
    ProblemDetail invalidRequest(RequestValidationException e) {
        return badRequest(e.errors());
    }

    @ExceptionHandler(DomainException.class)
    ProblemDetail domainRuleViolated(DomainException e) {
        return badRequest(List.of(new FieldError(null, e.getMessage())));
    }

    @ExceptionHandler(OutOfScopeLocationException.class)
    ProblemDetail outOfScope(OutOfScopeLocationException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
        problem.setTitle(e.suggestions().isEmpty() ? "Location out of scope" : "Ambiguous location");
        problem.setProperty("suggestions", e.suggestions());
        return problem;
    }

    @ExceptionHandler(InterpreterUnavailableException.class)
    ProblemDetail interpreterUnavailable(InterpreterUnavailableException e) {
        log.warn("Prompt could not be interpreted: {}", e.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Your request could not be read right now. Please try again in a minute.");
        problem.setTitle("Request reader unavailable");
        return problem;
    }

    @ExceptionHandler(RecordingController.RecordingNotFoundException.class)
    ProblemDetail recordingNotFound(RecordingController.RecordingNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Recording not found");
        return problem;
    }

    @ExceptionHandler(RecordingUnavailableException.class)
    ProblemDetail recordingUnavailable(RecordingUnavailableException e) {
        log.warn("Call recording could not be fetched: {}", e.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "The recording could not be fetched right now. Please try again later.");
        problem.setTitle("Recording unavailable");
        return problem;
    }

    /** Database unreachable or failing: a readable 503 instead of Spring's default 500 body. */
    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ProblemDetail storageUnavailable(RuntimeException e) {
        log.error("Storage failure: {}", e.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "The service is temporarily unavailable. Please try again in a minute.");
        problem.setTitle("Service unavailable");
        return problem;
    }

    /** The client went away (aborted download, broken pipe): nothing to send. */
    @ExceptionHandler(java.io.IOException.class)
    void clientGone(Exception e) {
        log.debug("Client disconnected: {}", e.getMessage());
    }

    /** Anything unexpected: still a ProblemDetail with a human sentence, never a stack trace. */
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("Unhandled error", e);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong on our side. Please try again.");
        problem.setTitle("Internal error");
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
        List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> new FieldError(f.getField(), f.getDefaultMessage()))
                .sorted(Comparator.comparing(FieldError::field).thenComparing(FieldError::message))
                .toList();
        return ResponseEntity.badRequest().body(badRequest(errors));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        return ResponseEntity.badRequest().body(badRequest(List.of(unreadableReason(ex))));
    }

    /** The field and reason for a bad value (wrong enum, malformed date), else a request-level message. */
    private static FieldError unreadableReason(HttpMessageNotReadableException ex) {
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
                return new FieldError(null, "Request body is not valid JSON");
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
            return new FieldError(field, reason);
        }
        return new FieldError(null, "Request body is missing or is not valid JSON");
    }

    private static ProblemDetail badRequest(List<FieldError> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                errors.stream().map(FieldError::toString).collect(Collectors.joining("; ")));
        problem.setTitle("Invalid request");
        problem.setProperty("errors", errors);
        return problem;
    }
}

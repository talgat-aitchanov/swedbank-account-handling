package ee.swedbank.api;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import ee.swedbank.service.AccountNotFoundException;
import ee.swedbank.service.ExternalLoggingFailedException;
import ee.swedbank.service.IdempotencySerializationException;
import ee.swedbank.service.InsufficientFundsException;
import ee.swedbank.service.InvalidRequestException;
import ee.swedbank.service.UnsupportedCurrencyException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final String UPSTREAM_DETAIL = "The request could not be processed due to an external dependency failure.";
    private static final String INTERNAL_DETAIL = "An unexpected error occurred.";

    // ── 404 ───────────────────────────────────────────────────────────────────

    /** Expected miss — the client referenced an ID that does not exist. */
    @ExceptionHandler(AccountNotFoundException.class)
    public ProblemDetail handleNotFound(AccountNotFoundException ex) {
        log.error("Account not found: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // ── 400 — domain (meaningful business events) ─────────────────────────────

    /** Meaningful business outcome: the caller attempted an operation they cannot afford. */
    @ExceptionHandler(InsufficientFundsException.class)
    public ProblemDetail handleInsufficientFunds(InsufficientFundsException ex) {
        log.error("Insufficient funds: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Meaningful business outcome: the caller referenced a currency with no balance. */
    @ExceptionHandler(UnsupportedCurrencyException.class)
    public ProblemDetail handleUnsupportedCurrency(UnsupportedCurrencyException ex) {
        log.error("No balance for requested currency: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Meaningful business outcome: the caller's request violates a business rule. */
    @ExceptionHandler(InvalidRequestException.class)
    public ProblemDetail handleInvalidRequest(InvalidRequestException ex) {
        log.error("Invalid business request: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // ── 400 — client input errors (caller sent bad data) ─────────────────────

    /** Client sent a request body that fails @Valid field constraints. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        List<String> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> "'" + fe.getField() + "' " + fe.getDefaultMessage()
                        + (fe.getRejectedValue() != null ? " (rejected value: " + fe.getRejectedValue() + ")" : ""))
                .collect(Collectors.toList());
        log.error("Bean validation failed: {}", violations);
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setDetail("Validation failed: " + String.join("; ", violations) + ".");
        pd.setProperty("violations", violations);
        return pd;
    }

    /** Client triggered a @Validated constraint on a path/query parameter. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        List<String> violations = ex.getConstraintViolations().stream()
                .map(cv -> {
                    String path = cv.getPropertyPath().toString();
                    String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
                    return "'" + field + "' " + cv.getMessage()
                            + (cv.getInvalidValue() != null ? " (rejected value: " + cv.getInvalidValue() + ")" : "");
                })
                .collect(Collectors.toList());
        log.error("Constraint violation: {}", violations);
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setDetail("Validation failed: " + String.join("; ", violations) + ".");
        pd.setProperty("violations", violations);
        return pd;
    }

    /** Client sent malformed JSON or an unrecognised enum value. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleNotReadable(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getCause();
        String detail;
        if (cause instanceof InvalidFormatException ife && ife.getTargetType() != null && ife.getTargetType().isEnum()) {
            String field = ife.getPath().isEmpty() ? "unknown"
                    : ife.getPath().get(ife.getPath().size() - 1).getFieldName();
            String rejected = String.valueOf(ife.getValue());
            String accepted = Arrays.stream(ife.getTargetType().getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
            detail = "Invalid value '" + rejected + "' for field '" + field
                    + "'. Accepted values are: " + accepted + ".";
        } else {
            detail = "Request body is missing or cannot be parsed.";
        }
        log.error("Unreadable request body: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    /** Client passed a path/query variable that cannot be converted to the required type. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String detail = "Invalid value '" + ex.getValue()
                + "' for parameter '" + ex.getName() + "'."
                + (ex.getRequiredType() != null ? " Expected type: " + ex.getRequiredType().getSimpleName() + "." : "");
        log.error("Type mismatch for parameter '{}': value='{}'", ex.getName(), ex.getValue());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    /** Client omitted a required request parameter. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParam(MissingServletRequestParameterException ex) {
        String detail = "Required parameter '" + ex.getParameterName() + "' of type '"
                + ex.getParameterType() + "' is missing.";
        log.error("Missing request parameter '{}' of type '{}'", ex.getParameterName(), ex.getParameterType());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    // ── 405 / 415 — protocol errors (client used wrong method / content-type) ─

    /** Client used an HTTP method not mapped to this endpoint. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        log.error("Method not allowed: {} {}", ex.getMethod(),
                ex.getSupportedHttpMethods() != null ? "— supported: " + ex.getSupportedHttpMethods() : "");
        return ProblemDetail.forStatusAndDetail(HttpStatus.METHOD_NOT_ALLOWED,
                "HTTP method not allowed for this endpoint.");
    }

    /** Client sent a content-type other than application/json. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        log.error("Unsupported media type: {}", ex.getContentType());
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Content type not supported. Use application/json.");
    }

    // ── 502 — third-party failure (server could not fulfil due to upstream) ───

    /** The external logging service was unreachable or returned a non-2xx response. */
    @ExceptionHandler(ExternalLoggingFailedException.class)
    public ProblemDetail handleExternalFailure(ExternalLoggingFailedException ex) {
        log.error("External dependency failure — withdrawal blocked: {}", ex.getMessage(), ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, UPSTREAM_DETAIL);
    }

    // ── 500 — server faults ───────────────────────────────────────────────────

    /** Idempotency record could not be serialized/deserialized — always a server bug. */
    @ExceptionHandler(IdempotencySerializationException.class)
    public ProblemDetail handleIdempotencySerialization(IdempotencySerializationException ex) {
        log.error("Idempotency serialization failure", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_DETAIL);
    }

    /** Last-resort handler — any exception not matched above is a server bug. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_DETAIL);
    }
}

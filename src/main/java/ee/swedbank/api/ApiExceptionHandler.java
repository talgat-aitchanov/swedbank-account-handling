package ee.swedbank.api;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import ee.swedbank.service.*;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Arrays;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final String UPSTREAM_DETAIL =
            "The request could not be processed due to an external dependency failure.";
    private static final String INTERNAL_DETAIL = "An unexpected error occurred.";

    // ── 4xx — expected client/domain outcomes ─────────────────────────────────

    @ExceptionHandler(AccountNotFoundException.class)
    public ProblemDetail handleNotFound(AccountNotFoundException ex) {
        log.info("Account not found: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(AccountAccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccountAccessDeniedException ex) {
        log.info("Account access denied: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler({InsufficientFundsException.class, UnsupportedCurrencyException.class, InvalidRequestException.class})
    public ProblemDetail handleBusinessRuleViolation(RuntimeException ex) {
        log.info("Business rule violation: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        List<String> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> "'" + fe.getField() + "' " + fe.getDefaultMessage()
                        + (fe.getRejectedValue() != null ? " (rejected value: " + fe.getRejectedValue() + ")" : ""))
                .toList();
        log.info("Validation failed: {}", violations);
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setDetail("Validation failed: " + String.join("; ", violations) + ".");
        pd.setProperty("violations", violations);
        return pd;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        List<String> violations = ex.getConstraintViolations().stream()
                .map(cv -> {
                    String path = cv.getPropertyPath().toString();
                    String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
                    return "'" + field + "' " + cv.getMessage()
                            + (cv.getInvalidValue() != null ? " (rejected value: " + cv.getInvalidValue() + ")" : "");
                })
                .toList();
        log.info("Constraint violation: {}", violations);
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setDetail("Validation failed: " + String.join("; ", violations) + ".");
        pd.setProperty("violations", violations);
        return pd;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleNotReadable(HttpMessageNotReadableException ex) {
        String detail;
        Throwable cause = ex.getCause();
        if (cause instanceof InvalidFormatException ife && ife.getTargetType() != null && ife.getTargetType().isEnum()) {
            String field = ife.getPath().isEmpty() ? "unknown"
                    : ife.getPath().get(ife.getPath().size() - 1).getFieldName();
            String rejected = String.valueOf(ife.getValue());
            String accepted = Arrays.stream(ife.getTargetType().getEnumConstants())
                    .map(Object::toString)
                    .collect(java.util.stream.Collectors.joining(", "));
            detail = "Invalid value '" + rejected + "' for field '" + field
                    + "'. Accepted values are: " + accepted + ".";
        } else {
            detail = "Request body is missing or cannot be parsed.";
        }
        log.info("Unreadable request body: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String detail = "Invalid value '" + ex.getValue()
                + "' for parameter '" + ex.getName() + "'."
                + (ex.getRequiredType() != null ? " Expected type: " + ex.getRequiredType().getSimpleName() + "." : "");
        log.info("Type mismatch for parameter '{}': value='{}'", ex.getName(), ex.getValue());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParam(MissingServletRequestParameterException ex) {
        String detail = "Required parameter '" + ex.getParameterName()
                + "' of type '" + ex.getParameterType() + "' is missing.";
        log.info("Missing request parameter '{}'", ex.getParameterName());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        log.info("Method not allowed: {}", ex.getMethod());
        return ProblemDetail.forStatusAndDetail(HttpStatus.METHOD_NOT_ALLOWED,
                "HTTP method not allowed for this endpoint.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        log.info("Unsupported media type: {}", ex.getContentType());
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Content type not supported. Use application/json.");
    }

    // ── 5xx — server faults ───────────────────────────────────────────────────

    @ExceptionHandler(ExternalLoggingFailedException.class)
    public ProblemDetail handleExternalFailure(ExternalLoggingFailedException ex) {
        log.error("External dependency failure: {}", ex.getMessage(), ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, UPSTREAM_DETAIL);
    }

    @ExceptionHandler(IdempotencySerializationException.class)
    public ProblemDetail handleIdempotencySerialization(IdempotencySerializationException ex) {
        log.error("Idempotency serialization failure", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_DETAIL);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneral(Exception ex) throws Exception {
        // Let Spring Security exceptions propagate to the filter chain's
        // AuthenticationEntryPoint / AccessDeniedHandler rather than absorbing them here.
        if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
            throw ex;
        }
        log.error("Unhandled exception", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_DETAIL);
    }
}

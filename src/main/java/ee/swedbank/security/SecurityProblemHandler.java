package ee.swedbank.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;

/**
 * Single component that writes RFC 9457 Problem Detail responses for
 * authentication (401) and authorisation (403) failures.
 * <p>
 * Used by both the security filter chain ({@link SecurityConfig}) and
 * {@link JwtAuthenticationFilter} so that every security error has
 * a consistent shape.
 */
@Component
@RequiredArgsConstructor
public class SecurityProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    /**
     * 401 — no valid credentials supplied.
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, "Authentication required.", request.getRequestURI());
    }

    /**
     * 403 — authenticated but lacking the required role.
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        write(response, HttpStatus.FORBIDDEN, "Access denied.", request.getRequestURI());
    }

    /**
     * Shared helper — also called directly from {@link JwtAuthenticationFilter}.
     */
    public void writeUnauthorized(HttpServletRequest request, HttpServletResponse response,
                                  String detail) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, detail, request.getRequestURI());
    }

    private void write(HttpServletResponse response, HttpStatus status,
                       String detail, String instance) throws IOException {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setInstance(URI.create(instance));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), pd);
    }
}


package ee.swedbank.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.*;

/**
 * Shorthand for {@code @AuthenticationPrincipal AppUserPrincipal}.
 * Use on controller method parameters to inject the authenticated user.
 *
 * <pre>{@code
 * public ResponseEntity<?> myEndpoint(@CurrentUser AppUserPrincipal user) { ... }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal
public @interface CurrentUser {
}


package ee.swedbank.security;

/**
 * Immutable principal placed into the SecurityContext after JWT validation.
 */
public record AppUserPrincipal(String username, String role) {
}

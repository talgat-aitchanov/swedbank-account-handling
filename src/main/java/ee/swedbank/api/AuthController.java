package ee.swedbank.api;

import ee.swedbank.api.dto.LoginRequest;
import ee.swedbank.api.dto.LoginResponse;
import ee.swedbank.repository.AppUserRepository;
import ee.swedbank.security.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AppUserRepository appUserRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        return appUserRepository.findByUsername(request.username())
                .filter(user -> passwordEncoder.matches(request.password(), user.getPasswordHash()))
                .map(user -> {
                    // Token carries username + role only; accountId is resolved from DB on each request
                    String token = jwtService.generateToken(user.getUsername(), user.getRole());
                    return ResponseEntity.ok((Object) new LoginResponse(token));
                })
                .orElseGet(() -> {
                    ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                            HttpStatus.UNAUTHORIZED, "Invalid username or password.");
                    pd.setInstance(URI.create("/auth/login"));
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(pd);
                });
    }
}

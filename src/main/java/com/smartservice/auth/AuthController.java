package com.smartservice.auth;

import com.smartservice.common.ApiResponse;
import com.smartservice.common.BusinessException;
import com.smartservice.security.JwtService;
import com.smartservice.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(AuthRepository repository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthRepository.UserAccount account = repository.findByUsername(request.username())
                .filter(it -> passwordEncoder.matches(request.password(), it.passwordHash()))
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));
        UserPrincipal principal = new UserPrincipal(account.id(), account.username(), account.displayName(), account.role());
        return ApiResponse.ok(new LoginResponse(jwtService.issue(principal), principal));
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record LoginResponse(String token, UserPrincipal user) {}
}

package com.synapse.auth.controller;

import com.synapse.auth.dto.LoginRequest;
import com.synapse.auth.dto.RegisterRequest;
import com.synapse.auth.vo.LoginResponse;
import com.synapse.auth.vo.UserVO;
import com.synapse.auth.service.AuthService;
import com.synapse.common.api.Result;
import com.synapse.common.constant.SecurityConstants;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * Auth endpoints, all under {@code /api/auth} (the gateway routes this prefix here).
 * {@code /login} and {@code /register} are on the gateway whitelist (no token needed);
 * {@code /info} trusts the {@code X-User-Id} header the gateway injects after verifying the JWT.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return Result.ok(authService.login(req));
    }

    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterRequest req) {
        return Result.ok(authService.register(req));
    }

    @GetMapping("/info")
    public Result<UserVO> info(@RequestHeader(SecurityConstants.USER_ID_HEADER) String userId) {
        return Result.ok(authService.info(userId));
    }
}

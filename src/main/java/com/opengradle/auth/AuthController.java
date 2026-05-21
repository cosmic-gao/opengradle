package com.opengradle.auth;

import com.opengradle.auth.dto.LoginRequest;
import com.opengradle.auth.dto.LoginResponse;
import com.opengradle.constant.Constant;
import com.opengradle.exception.ErrorCode;
import com.opengradle.utils.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Auth REST endpoints.
 *
 * <ul>
 *   <li>{@code POST /auth/login}  &mdash; credentials in body, token in response</li>
 *   <li>{@code POST /auth/logout} &mdash; invalidates the token from the {@code token} header</li>
 *   <li>{@code GET  /auth/me}     &mdash; returns the current UserContext for diagnostics</li>
 * </ul>
 *
 * <p>{@code /auth/login} must be in the gateway whitelist
 * (see {@code application.yml}), otherwise the AuthFilter will reject it as
 * unauthenticated.
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Mono<Result<LoginResponse>> login(@RequestBody LoginRequest request) {
        return Mono.fromCallable(() -> authService.login(request))
                .map(opt -> opt
                        .map(Result::of)
                        .orElseGet(() -> Result.fail(ErrorCode.UNAUTHORIZED, "invalid username or password")));
    }

    @PostMapping("/logout")
    public Mono<Result<Boolean>> logout(ServerHttpRequest request) {
        String token = request.getHeaders().getFirst(Constant.TOKEN_HEADER);
        return Mono.fromCallable(() -> authService.logout(token))
                .map(Result::of);
    }

    @GetMapping("/me")
    public Mono<Result<UserContext>> me(ServerHttpRequest request) {
        String token = request.getHeaders().getFirst(Constant.TOKEN_HEADER);
        return Mono.fromCallable(() -> authService.getUserContext(token))
                .map(opt -> opt
                        .map(Result::of)
                        .orElseGet(() -> Result.fail(ErrorCode.UNAUTHORIZED, "token invalid or expired")));
    }
}

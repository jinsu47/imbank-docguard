package com.imbank.docguard.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 인증 API (ARCHITECTURE.md 5장). 로그아웃은 SecurityConfig 의 logout 설정이 처리합니다. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** CSRF 토큰 쿠키(XSRF-TOKEN)를 발급합니다. 화면은 상태를 바꾸는 요청마다 X-XSRF-TOKEN 헤더로 돌려보냅니다. */
    @GetMapping("/csrf")
    public AuthDtos.CsrfResponse csrf(CsrfToken token) {
        // getToken() 호출로 지연 생성된 토큰을 만들어 쿠키에 싣습니다.
        token.getToken();
        return new AuthDtos.CsrfResponse(token.getHeaderName());
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthDtos.MeResponse signup(@Valid @RequestBody AuthDtos.SignupRequest request, HttpServletRequest httpRequest) {
        return AuthDtos.MeResponse.from(authService.signup(request, httpRequest.getRemoteAddr()));
    }

    @PostMapping("/login")
    public AuthDtos.MeResponse login(@Valid @RequestBody AuthDtos.LoginRequest request, HttpServletRequest httpRequest,
                                     HttpServletResponse httpResponse) {
        return AuthDtos.MeResponse.from(authService.login(request, httpRequest, httpResponse));
    }

    @GetMapping("/me")
    public AuthDtos.MeResponse me() {
        return AuthDtos.MeResponse.from(CurrentUser.require());
    }
}

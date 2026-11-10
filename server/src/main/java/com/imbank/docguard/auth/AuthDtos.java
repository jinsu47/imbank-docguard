package com.imbank.docguard.auth;

import com.imbank.docguard.user.Role;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 인증 API 요청·응답 형식 */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** 회원가입. 본문에 role 이 있어도 받지 않습니다(항상 INSPECTOR). */
    public record SignupRequest(
            @NotBlank(message = "이름을 입력해 주세요.") @Size(max = 50, message = "이름은 50자 이하로 입력해 주세요.")
            String displayName,
            @NotBlank(message = "이메일을 입력해 주세요.") @Email(message = "이메일 형식이 올바르지 않습니다.")
            @Size(max = 254, message = "이메일이 너무 깁니다.")
            String email,
            @NotBlank(message = "비밀번호를 입력해 주세요.")
            String password,
            @AssertTrue(message = "개인정보 처리 안내에 동의해 주세요.")
            boolean agreed) {
    }

    public record LoginRequest(
            @NotBlank(message = "이메일을 입력해 주세요.") @Size(max = 254) String email,
            @NotBlank(message = "비밀번호를 입력해 주세요.") @Size(max = 128) String password) {
    }

    public record MeResponse(Long id, String email, String displayName, Role role) {
        static MeResponse from(AuthUser user) {
            return new MeResponse(user.id(), user.email(), user.displayName(), user.role());
        }
    }

    /** 토큰 값은 XSRF-TOKEN 쿠키로만 줍니다. 화면은 쿠키 값을 headerName 헤더에 넣습니다. */
    public record CsrfResponse(String headerName) {
    }
}

package com.imbank.docguard.auth;

import com.imbank.docguard.user.Role;
import com.imbank.docguard.user.User;

import java.io.Serial;
import java.io.Serializable;

/** 세션에 두는 로그인 사용자 정보. 비밀번호 해시는 넣지 않습니다. */
public record AuthUser(Long id, String email, String displayName, Role role) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static AuthUser from(User user) {
        return new AuthUser(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole());
    }

    public boolean is(Role expected) {
        return role == expected;
    }
}

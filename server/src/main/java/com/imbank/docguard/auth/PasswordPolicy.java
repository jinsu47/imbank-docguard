package com.imbank.docguard.auth;

import com.imbank.docguard.common.ApiException;

import java.util.Locale;

/**
 * 비밀번호 규칙 (SECURITY.md 2장): 10자 이상, 영문과 숫자를 모두 포함, 이메일과 같으면 안 됨.
 * BCrypt 는 72바이트까지만 쓰므로 최대 64자로 제한합니다.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 64;

    private PasswordPolicy() {
    }

    public static void validate(String password, String email) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw ApiException.badRequest("WEAK_PASSWORD", "비밀번호는 10자 이상 64자 이하로 입력해 주세요.");
        }
        boolean hasLetter = password.chars().anyMatch(c -> (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'));
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw ApiException.badRequest("WEAK_PASSWORD", "비밀번호에는 영문과 숫자를 모두 넣어 주세요.");
        }
        if (email != null) {
            String lower = password.toLowerCase(Locale.ROOT);
            String mail = email.toLowerCase(Locale.ROOT);
            String localPart = mail.contains("@") ? mail.substring(0, mail.indexOf('@')) : mail;
            if (lower.equals(mail) || lower.equals(localPart)) {
                throw ApiException.badRequest("WEAK_PASSWORD", "이메일과 같은 비밀번호는 쓸 수 없습니다.");
            }
        }
    }
}

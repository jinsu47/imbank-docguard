package com.imbank.docguard.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/** 사용자 (DATABASE.md 3장 users). 삭제하지 않고 DISABLED 로만 바꿉니다. */
@Entity
@Table(name = "users")
public class User {

    /** 로그인 연속 실패 허용 횟수와 잠금 시간 (SECURITY.md 2장) */
    public static final int MAX_FAILED_LOGINS = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    public User(String email, String displayName, String passwordHash, Role role) {
        this.email = normalizeEmail(email);
        this.displayName = displayName.strip();
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = UserStatus.ACTIVE;
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** 잠금 시간이 지났으면 풀어 줍니다. 지금 잠겨 있으면 true. */
    public boolean refreshLock(Instant now) {
        if (status == UserStatus.LOCKED && lockedUntil != null && !now.isBefore(lockedUntil)) {
            status = UserStatus.ACTIVE;
            lockedUntil = null;
            failedLoginCount = 0;
        }
        return status == UserStatus.LOCKED;
    }

    /** 로그인 실패를 기록합니다. 이번 실패로 잠겼으면 true. */
    public boolean recordLoginFailure(Instant now) {
        failedLoginCount++;
        if (failedLoginCount >= MAX_FAILED_LOGINS) {
            status = UserStatus.LOCKED;
            lockedUntil = now.plus(LOCK_DURATION);
            return true;
        }
        return false;
    }

    /** 관리자의 역할 변경 (SECURITY.md 1장). 자기 역할 변경 금지는 서비스에서 확인합니다. */
    public void changeRole(Role newRole) {
        this.role = newRole;
    }

    /**
     * 관리자의 상태 변경 (DOMAIN.md 5장 사용자 상태).
     * 관리자가 잠그면 해제 시각이 없고(직접 풀어야 함), 해제하면 실패 횟수도 지웁니다.
     */
    public void changeStatus(UserStatus newStatus) {
        this.status = newStatus;
        this.lockedUntil = null;
        if (newStatus == UserStatus.ACTIVE) {
            this.failedLoginCount = 0;
        }
    }

    /** 관리자가 잠갔거나 비활성화해서 이미 로그인한 세션도 끊어야 하는 상태인지 */
    public boolean blockedByAdmin() {
        return status == UserStatus.DISABLED || (status == UserStatus.LOCKED && lockedUntil == null);
    }

    public void recordLoginSuccess(Instant now) {
        failedLoginCount = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public int getFailedLoginCount() {
        return failedLoginCount;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

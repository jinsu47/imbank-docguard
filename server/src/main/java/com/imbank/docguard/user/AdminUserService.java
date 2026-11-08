package com.imbank.docguard.user;

import com.imbank.docguard.audit.AuditAction;
import com.imbank.docguard.audit.AuditService;
import com.imbank.docguard.auth.AuthUser;
import com.imbank.docguard.common.ApiException;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 관리자의 계정 관리 (F10, SECURITY.md 1장).
 * 역할·상태를 바꿀 때마다 이전 값과 새 값을 감사 기록에 남깁니다. 관리자는 자기 역할과 상태를 바꿀 수 없습니다.
 */
@Service
public class AdminUserService {

    private final UserRepository users;
    private final AuditService audit;

    public AdminUserService(UserRepository users, AuditService audit) {
        this.users = users;
        this.audit = audit;
    }

    public record UserRow(Long id, String email, String displayName, Role role, UserStatus status,
                          int failedLoginCount, Instant lockedUntil, Instant lastLoginAt, Instant createdAt) {
        static UserRow from(User u) {
            return new UserRow(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole(), u.getStatus(),
                    u.getFailedLoginCount(), u.getLockedUntil(), u.getLastLoginAt(), u.getCreatedAt());
        }
    }

    /** 바꿀 값만 보냅니다. */
    public record UpdateRequest(Role role, UserStatus status, @Size(max = 300) String reason) {
    }

    @Transactional(readOnly = true)
    public List<UserRow> list() {
        return users.findAll(Sort.by(Sort.Direction.ASC, "id")).stream().map(UserRow::from).toList();
    }

    @Transactional
    public UserRow update(AuthUser admin, Long id, UpdateRequest request) {
        User user = users.findById(id).orElseThrow(ApiException::notFound);
        if (request.role() == null && request.status() == null) {
            throw ApiException.badRequest("NOTHING_TO_CHANGE", "바꿀 역할이나 상태를 보내 주세요.");
        }
        if (user.getId().equals(admin.id())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SELF_CHANGE", "자기 계정의 역할과 상태는 바꿀 수 없습니다.");
        }
        if (request.role() != null && request.role() != user.getRole()) {
            Role before = user.getRole();
            user.changeRole(request.role());
            audit.record(AuditAction.USER_ROLE_CHANGED, admin.id(), "USER", user.getId().toString(), request.reason(),
                    change(before.name(), request.role().name()));
        }
        if (request.status() != null && request.status() != user.getStatus()) {
            UserStatus before = user.getStatus();
            user.changeStatus(request.status());
            audit.record(AuditAction.USER_STATUS_CHANGED, admin.id(), "USER", user.getId().toString(), request.reason(),
                    change(before.name(), request.status().name()));
        }
        return UserRow.from(user);
    }

    private static Map<String, Object> change(String before, String after) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("before", before);
        detail.put("after", after);
        return detail;
    }
}

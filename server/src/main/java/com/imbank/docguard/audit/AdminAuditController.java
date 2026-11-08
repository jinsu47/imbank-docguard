package com.imbank.docguard.audit;

import com.imbank.docguard.user.User;
import com.imbank.docguard.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 감사 기록 조회 (F09, SECURITY.md 7장). 경로 규칙상 ADMIN 만 들어옵니다. 조회만 하고 수정·삭제 API 는 두지 않습니다.
 * 기간, 사용자, 행동으로 거를 수 있습니다(USER_FLOW.md 1-3).
 */
@RestController
@RequestMapping("/api/admin/audit-logs")
public class AdminAuditController {

    private final AuditLogRepository logs;
    private final UserRepository users;

    public AdminAuditController(AuditLogRepository logs, UserRepository users) {
        this.logs = logs;
        this.users = users;
    }

    public record AuditRow(Long id, Instant createdAt, Long actorId, String actorEmail, AuditAction action,
                           String targetType, String targetId, String reason, String ipAddress, String requestId,
                           Map<String, Object> detail) {
    }

    public record AuditPage(List<AuditRow> items, int page, int size, long totalElements, int totalPages) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public AuditPage search(@RequestParam(required = false) AuditAction action,
                            @RequestParam(required = false) Long actorId,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "50") int size) {
        List<Specification<AuditLog>> filters = new ArrayList<>();
        if (action != null) {
            filters.add((root, q, cb) -> cb.equal(root.get("action"), action));
        }
        if (actorId != null) {
            filters.add((root, q, cb) -> cb.equal(root.get("actorId"), actorId));
        }
        if (from != null) {
            filters.add((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            filters.add((root, q, cb) -> cb.lessThan(root.get("createdAt"), to));
        }
        Specification<AuditLog> spec = Specification.allOf(filters);
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200),
                Sort.by(Sort.Direction.DESC, "id"));
        Page<AuditLog> result = logs.findAll(spec, pageable);

        Map<Long, String> emails = users.findAllById(result.stream().map(AuditLog::getActorId)
                        .filter(java.util.Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, User::getEmail, (a, b) -> a));
        Function<AuditLog, AuditRow> toRow = l -> new AuditRow(l.getId(), l.getCreatedAt(), l.getActorId(),
                l.getActorId() == null ? null : emails.get(l.getActorId()), l.getAction(), l.getTargetType(),
                l.getTargetId(), l.getReason(), l.getIpAddress(), l.getRequestId(), l.getDetail());
        return new AuditPage(result.stream().map(toRow).toList(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }
}

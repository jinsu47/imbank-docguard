package com.imbank.docguard.audit;

import com.imbank.docguard.common.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/**
 * 감사 기록을 남깁니다. 요청 중이면 IP, User-Agent, 요청 ID 를 함께 적습니다. (SECURITY.md 7장)
 * 바깥 트랜잭션이 실패해도 기록이 남도록 새 트랜잭션으로 저장합니다.
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditAction action, Long actorId, String targetType, String targetId, String reason,
                       Map<String, Object> detail) {
        String ip = null;
        String userAgent = null;
        String requestId = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            ip = truncate(request.getRemoteAddr(), 45);
            userAgent = truncate(request.getHeader("User-Agent"), 300);
            requestId = RequestIdFilter.current();
        }
        repository.save(new AuditLog(actorId, action, targetType, targetId, truncate(reason, 300),
                ip, userAgent, requestId, detail));
    }

    public void record(AuditAction action, Long actorId) {
        record(action, actorId, null, null, null, null);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}

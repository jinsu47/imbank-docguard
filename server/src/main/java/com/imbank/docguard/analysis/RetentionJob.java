package com.imbank.docguard.analysis;

import com.imbank.docguard.audit.AuditAction;
import com.imbank.docguard.audit.AuditService;
import com.imbank.docguard.result.AnalysisResultService;
import com.imbank.docguard.storage.FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 보관 기간 정리 (F11, BR15). expires_at 이 지난 분석의 원본 파일, 분석 결과, 발견 항목을 지우고 PURGED 로 바꿉니다.
 * 등급, 검토 상태, 시각 같은 요약은 남깁니다.
 */
@Component
public class RetentionJob {

    private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);

    private final AnalysisRepository analyses;
    private final AnalysisResultService results;
    private final FileStorage storage;
    private final AuditService audit;
    private final TransactionTemplate tx;

    public RetentionJob(AnalysisRepository analyses, AnalysisResultService results, FileStorage storage,
                        AuditService audit, TransactionTemplate tx) {
        this.analyses = analyses;
        this.results = results;
        this.storage = storage;
        this.audit = audit;
        this.tx = tx;
    }

    public int purgeExpired() {
        Instant now = Instant.now();
        List<UUID> ids = analyses.findExpired(now).stream().map(Analysis::getId).toList();
        int purged = 0;
        for (UUID id : ids) {
            try {
                tx.executeWithoutResult(status -> analyses.findById(id).ifPresent(a -> {
                    results.delete(id);
                    storage.delete(a.getStoredPath());
                    a.markPurged(now);
                }));
                audit.record(AuditAction.ANALYSIS_PURGED, null, "ANALYSIS", id.toString(), null, null);
                purged++;
            } catch (RuntimeException e) {
                log.error("보관 기간 정리 실패: id={} type={}", id, e.getClass().getSimpleName());
            }
        }
        if (purged > 0) {
            log.info("보관 기간 정리: {}건", purged);
        }
        return purged;
    }
}

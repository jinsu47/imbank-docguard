package com.imbank.docguard.analysis;

import com.imbank.docguard.result.AnalysisResultService;
import com.imbank.docguard.storage.EncryptionService;
import com.imbank.docguard.storage.FileStorage;
import com.imbank.docguard.studio.StudioException;
import com.imbank.docguard.studio.StudioModels;
import com.imbank.docguard.studio.StudioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 진행 중인 분석을 Studio 와 맞춥니다 (ARCHITECTURE.md 4장 호출 순서, 8장 장애 대응).
 * <ul>
 *   <li>UPLOADING: 원본을 Studio 에 올리고 작업을 만들어 QUEUED 로 바꿉니다.</li>
 *   <li>QUEUED·RUNNING: 작업을 조회해 진행 단계를 갱신하고, 끝나면 결과를 저장합니다.</li>
 *   <li>429·5xx·네트워크 오류는 다음 주기에 다시 시도하고, 3회 연속 실패하거나 시간이 초과되면 FAILED 로 바꿉니다.</li>
 *   <li>작업이 끝나면(완료·실패 모두) Studio 에 올린 파일을 지웁니다(BR07). 지우지 못하면 다음 주기에 다시 지웁니다.</li>
 * </ul>
 * DB 에 상태가 있으므로 서버가 다시 시작되어도 이어서 처리합니다. 서버 한 대를 전제로 합니다.
 */
@Component
public class AnalysisJobProcessor {

    private static final Logger log = LoggerFactory.getLogger(AnalysisJobProcessor.class);
    static final int MAX_ATTEMPTS = 3;

    private final AnalysisRepository analyses;
    private final StudioService studio;
    private final AnalysisResultService results;
    private final FileStorage storage;
    private final EncryptionService encryption;
    private final TransactionTemplate tx;
    private final Clock clock;
    /** 분석별 연속 실패 횟수(메모리). 서버를 다시 시작하면 0 부터 셉니다. */
    private final Map<UUID, Integer> failures = new ConcurrentHashMap<>();

    public AnalysisJobProcessor(AnalysisRepository analyses, StudioService studio, AnalysisResultService results,
                                FileStorage storage, EncryptionService encryption, TransactionTemplate tx) {
        this.analyses = analyses;
        this.studio = studio;
        this.results = results;
        this.storage = storage;
        this.encryption = encryption;
        this.tx = tx;
        this.clock = Clock.systemUTC();
    }

    /** 한 주기 처리. 스케줄러가 부르고, 테스트에서도 직접 부릅니다. */
    public void processAll() {
        List<UUID> ids = analyses.findByStatusInOrderByCreatedAtAsc(AnalysisStatus.IN_PROGRESS).stream()
                .map(Analysis::getId).toList();
        for (UUID id : ids) {
            try {
                tx.executeWithoutResult(status -> processOne(id));
            } catch (RuntimeException e) {
                log.error("분석 처리 중 오류: id={} type={}", id, e.getClass().getSimpleName());
            }
        }
        cleanupStudioFiles();
    }

    private void processOne(UUID id) {
        Analysis a = analyses.findById(id).orElse(null);
        if (a == null || !a.getStatus().inProgress()) {
            return;
        }
        switch (a.getStatus()) {
            case UPLOADING -> submit(a);
            case QUEUED, RUNNING -> poll(a);
            default -> { }
        }
    }

    private void submit(Analysis a) {
        Instant now = clock.instant();
        try {
            byte[] content = storage.read(a.getStoredPath());
            // 실제 호출에서는 파일 이름을 보내지 않습니다. 재생 모드만 저장 응답을 고르는 데 씁니다.
            String hint = studio.replayMode() ? encryption.decryptText(a.getOriginalFilenameEnc()) : null;
            StudioModels.Submission submission = studio.submit(content, a.getContentType(), hint);
            a.markQueued(submission.fileId(), submission.jobId(), now);
            failures.remove(a.getId());
            log.info("Studio 작업 생성: id={} job={}", a.getId(), submission.jobId());
        } catch (StudioException e) {
            if (e.retryable() && countFailure(a.getId()) < MAX_ATTEMPTS) {
                log.warn("Studio 업로드 재시도 예정: id={} code={}", a.getId(), e.code());
                return;
            }
            String code = "STUDIO_NOT_CONFIGURED".equals(e.code()) || "STUDIO_REPLAY_EMPTY".equals(e.code())
                    ? e.code() : "STUDIO_UPLOAD_FAILED";
            fail(a, code, now);
        }
    }

    private void poll(Analysis a) {
        Instant now = clock.instant();
        Instant started = a.getSubmittedAt() != null ? a.getSubmittedAt() : a.getCreatedAt();
        if (Duration.between(started, now).getSeconds() > studio.jobTimeoutSec()) {
            fail(a, "STUDIO_TIMEOUT", now);
            return;
        }
        StudioModels.JobSnapshot snapshot;
        try {
            snapshot = studio.poll(a.getStudioJobId());
        } catch (StudioException e) {
            // 429·5xx·네트워크 오류는 시간 초과 전까지 계속 다시 시도하고, 그 밖의 오류는 3회 연속이면 실패로 봅니다.
            if (e.retryable() || countFailure(a.getId()) < MAX_ATTEMPTS) {
                log.warn("Studio 작업 조회 실패, 다음 주기에 다시 시도: id={} code={}", a.getId(), e.code());
                return;
            }
            fail(a, "STUDIO_JOB_FAILED", now);
            return;
        } catch (RuntimeException e) {
            log.error("Studio 응답 변환 실패: id={} type={}", a.getId(), e.getClass().getSimpleName());
            fail(a, "STUDIO_BAD_RESPONSE", now);
            return;
        }
        failures.remove(a.getId());
        List<String> steps = snapshot.completedSteps().stream().map(Enum::name).toList();
        switch (snapshot.status()) {
            case QUEUED -> a.updateSteps(steps);
            case IN_PROGRESS -> a.markRunning(steps);
            case FAILED -> fail(a, "STUDIO_JOB_FAILED", now);
            case COMPLETED -> complete(a, snapshot, steps, now);
        }
    }

    private void complete(Analysis a, StudioModels.JobSnapshot snapshot, List<String> steps, Instant now) {
        StudioModels.Outcome outcome = snapshot.outcome();
        AnalysisResultService.Verdict verdict = results.store(a.getId(), outcome, now);
        a.markCompleted(new Analysis.CompletedResult(steps, outcome.pageCount(), outcome.documentType(),
                outcome.documentTypeConfidence(), verdict.grade(), verdict.provisional(), verdict.reviewRequired(),
                outcome.parseCheckVerdict(), outcome.extractCheckVerdict(), verdict.findingCount()), now);
        if (!verdict.reportParsed()) {
            log.warn("보고서를 해석하지 못해 등급 UNKNOWN, 사람 검토 필요로 저장: id={}", a.getId());
        }
        long seconds = a.getSubmittedAt() == null ? -1 : Duration.between(a.getSubmittedAt(), now).getSeconds();
        log.info("분석 완료: id={} grade={} review={} findings={} elapsed={}s", a.getId(), verdict.grade(),
                verdict.reviewRequired(), verdict.findingCount(), seconds);
        deleteStudioFile(a);
    }

    private void fail(Analysis a, String code, Instant now) {
        a.markFailed(code, now);
        failures.remove(a.getId());
        log.warn("분석 실패: id={} code={}", a.getId(), code);
        deleteStudioFile(a);
    }

    private void deleteStudioFile(Analysis a) {
        if (a.getStudioFileId() != null && studio.deleteFileQuietly(a.getStudioFileId())) {
            a.clearStudioFile();
        }
    }

    /** 완료·실패했는데 Studio 파일이 남아 있으면 다시 지웁니다. */
    private void cleanupStudioFiles() {
        List<UUID> ids = analyses.findByStudioFileIdIsNotNullAndStatusIn(
                List.of(AnalysisStatus.COMPLETED, AnalysisStatus.FAILED, AnalysisStatus.PURGED)).stream()
                .map(Analysis::getId).toList();
        for (UUID id : ids) {
            try {
                tx.executeWithoutResult(status -> analyses.findById(id).ifPresent(this::deleteStudioFile));
            } catch (RuntimeException e) {
                log.warn("Studio 파일 정리 실패: id={}", id);
            }
        }
    }

    private int countFailure(UUID id) {
        return failures.merge(id, 1, Integer::sum);
    }
}

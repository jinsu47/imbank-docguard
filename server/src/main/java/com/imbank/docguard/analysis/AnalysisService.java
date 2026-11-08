package com.imbank.docguard.analysis;

import com.imbank.docguard.audit.AuditAction;
import com.imbank.docguard.audit.AuditService;
import com.imbank.docguard.auth.AuthUser;
import com.imbank.docguard.common.ApiException;
import com.imbank.docguard.common.AppProperties;
import com.imbank.docguard.common.RateLimiter;
import com.imbank.docguard.result.AnalysisResultService;
import com.imbank.docguard.result.ResultDtos;
import com.imbank.docguard.review.Review;
import com.imbank.docguard.review.ReviewRepository;
import com.imbank.docguard.storage.EncryptionService;
import com.imbank.docguard.storage.FileStorage;
import com.imbank.docguard.studio.StudioModels;
import com.imbank.docguard.studio.StudioService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 분석 생성·조회·삭제·다시 실행 (F02~F07). Studio 호출은 {@link AnalysisJobProcessor}가 스케줄러에서 합니다.
 * 모든 조회는 {@link AnalysisAccessPolicy}로 소유자·검토 범위를 확인합니다.
 */
@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);
    private static final String VIEWED_FILES_SESSION_KEY = "docguard.viewedFiles";

    private final AnalysisRepository analyses;
    private final ReviewRepository reviews;
    private final AnalysisAccessPolicy policy;
    private final UploadValidator validator;
    private final FileStorage storage;
    private final EncryptionService encryption;
    private final AnalysisResultService results;
    private final StudioService studio;
    private final AuditService audit;
    private final RateLimiter rateLimiter;
    private final AppProperties properties;

    public AnalysisService(AnalysisRepository analyses, ReviewRepository reviews, AnalysisAccessPolicy policy,
                           UploadValidator validator, FileStorage storage, EncryptionService encryption,
                           AnalysisResultService results, StudioService studio, AuditService audit,
                           RateLimiter rateLimiter, AppProperties properties) {
        this.analyses = analyses;
        this.reviews = reviews;
        this.policy = policy;
        this.validator = validator;
        this.storage = storage;
        this.encryption = encryption;
        this.results = results;
        this.studio = studio;
        this.audit = audit;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    // ------------------------------------------------------------------ 생성

    @Transactional
    public UUID create(AuthUser user, String originalFilename, byte[] content) {
        UploadValidator.ValidatedFile file = validator.validate(content);

        String storedPath = storage.save(content);
        Instant now = Instant.now();
        Analysis analysis = Analysis.create(user.id(), encryption.encryptText(safeFilename(originalFilename)), storedPath,
                file.sha256(), content.length, file.contentType(), file.pageCount(), studio.agentIdForRecord(),
                studio.configIdForRecord(), Duration.ofHours(properties.retentionHours()), now);
        try {
            analyses.save(analysis);
        } catch (RuntimeException e) {
            storage.delete(storedPath);
            throw e;
        }
        audit.record(AuditAction.ANALYSIS_CREATED, user.id(), "ANALYSIS", analysis.getId().toString(), null,
                Map.of("contentType", file.contentType(), "pageCount", file.pageCount()));
        log.info("분석 생성: id={} pages={}", analysis.getId(), file.pageCount());
        return analysis.getId();
    }

    /** 경로를 떼고 길이를 줄입니다. 화면 표시용으로만 암호화해 저장합니다. */
    static String safeFilename(String name) {
        if (name == null || name.isBlank()) {
            return "document";
        }
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).strip();
        if (base.isEmpty()) {
            base = "document";
        }
        return base.length() <= 200 ? base : base.substring(0, 200);
    }

    // ------------------------------------------------------------------ 조회

    @Transactional(readOnly = true)
    public List<AnalysisDtos.AnalysisSummary> listMine(AuthUser user) {
        return analyses.findTop100ByOwnerIdOrderByCreatedAtDesc(user.id()).stream()
                .map(a -> summary(a, true))
                .toList();
    }

    @Transactional(readOnly = true)
    public AnalysisDtos.AnalysisDetail detail(AuthUser user, UUID id) {
        Analysis a = load(id);
        policy.requireView(user, a);
        AnalysisDtos.ReviewInfo review = reviews.findByAnalysisId(id)
                .map(r -> new AnalysisDtos.ReviewInfo(r.getDecision(), r.getComment(), r.getCreatedAt()))
                .orElse(null);
        List<String> steps = Arrays.stream(StudioModels.Step.values()).map(Enum::name).toList();
        return new AnalysisDtos.AnalysisDetail(summary(a, true), a.getContentType(), a.getStudioConfigId(),
                a.getParseCheckVerdict(), a.getExtractCheckVerdict(), steps, a.getCompletedSteps(), a.getSubmittedAt(),
                a.getExpiresAt(), a.getPurgedAt(), policy.isOwner(user, a), policy.canManage(user, a), policy.canReview(user, a), review);
    }

    @Transactional(readOnly = true)
    public ResultDtos.ResultView result(AuthUser user, UUID id) {
        Analysis a = load(id);
        policy.requireView(user, a);
        if (a.getStatus() == AnalysisStatus.PURGED) {
            throw new ApiException(HttpStatus.GONE, "PURGED", "보관 기간이 지나 원본과 상세 결과를 삭제했습니다.");
        }
        if (a.getStatus() != AnalysisStatus.COMPLETED || !results.exists(id)) {
            throw ApiException.conflict("NOT_COMPLETED", "분석이 아직 끝나지 않았습니다.");
        }
        return results.view(id);
    }

    public record FileContent(byte[] content, String contentType) {
    }

    /** 원본 파일. 세션마다 분석별로 처음 열 때만 감사 기록을 남깁니다(SECURITY.md 7장). */
    @Transactional(readOnly = true)
    public FileContent file(AuthUser user, UUID id, HttpSession session) {
        Analysis a = load(id);
        policy.requireView(user, a);
        if (a.getStoredPath() == null) {
            throw new ApiException(HttpStatus.GONE, "PURGED", "보관 기간이 지나 원본을 삭제했습니다.");
        }
        byte[] content = storage.read(a.getStoredPath());
        @SuppressWarnings("unchecked")
        Set<String> viewed = (Set<String>) session.getAttribute(VIEWED_FILES_SESSION_KEY);
        if (viewed == null) {
            viewed = new HashSet<>();
        }
        if (viewed.add(id.toString())) {
            session.setAttribute(VIEWED_FILES_SESSION_KEY, viewed);
            audit.record(AuditAction.ORIGINAL_FILE_VIEWED, user.id(), "ANALYSIS", id.toString(), null, null);
        }
        return new FileContent(content, a.getContentType());
    }

    /** 원문 보기 (BR08): 사유 필수, 감사 기록을 남긴 뒤에만 원문값을 돌려줍니다. */
    public List<ResultDtos.RevealedFinding> reveal(AuthUser user, UUID id, String reason) {
        rateLimiter.check("reveal:" + user.id(), 30, Duration.ofMinutes(1), "원문 보기 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.");
        Analysis a = load(id);
        policy.requireView(user, a);
        if (a.getStatus() != AnalysisStatus.COMPLETED || !results.exists(id)) {
            throw ApiException.conflict("NOT_COMPLETED", "분석이 끝난 문서만 원문을 볼 수 있습니다.");
        }
        audit.record(AuditAction.RAW_VALUE_REVEALED, user.id(), "ANALYSIS", id.toString(), reason.strip(), null);
        return results.reveal(id);
    }

    // ------------------------------------------------------------------ 삭제와 다시 실행

    /** 소유자 또는 관리자 삭제 (BR16). 진행 중인 분석은 끝난 뒤 지울 수 있습니다. */
    @Transactional
    public void delete(AuthUser user, UUID id) {
        Analysis a = load(id);
        policy.requireManage(user, a);
        if (a.getStatus().inProgress()) {
            throw ApiException.conflict("IN_PROGRESS", "진행 중인 분석은 끝난 뒤 지울 수 있습니다.");
        }
        if (a.getStudioFileId() != null) {
            studio.deleteFileQuietly(a.getStudioFileId());
        }
        results.delete(id);
        reviews.deleteByAnalysisId(id);
        storage.delete(a.getStoredPath());
        analyses.delete(a);
        audit.record(AuditAction.ANALYSIS_DELETED, user.id(), "ANALYSIS", id.toString(), null,
                Map.of("ownerId", a.getOwnerId()));
        log.info("분석 삭제: id={}", id);
    }

    /** 다시 실행: FAILED → UPLOADING (소유자 또는 관리자) */
    @Transactional
    public void retry(AuthUser user, UUID id) {
        Analysis a = load(id);
        policy.requireManage(user, a);
        if (a.getStatus() != AnalysisStatus.FAILED) {
            throw ApiException.conflict("NOT_FAILED", "실패한 분석만 다시 실행할 수 있습니다.");
        }
        if (a.getStoredPath() == null) {
            throw new ApiException(HttpStatus.GONE, "PURGED", "원본이 삭제되어 다시 실행할 수 없습니다.");
        }
        if (a.getStudioFileId() != null) {
            studio.deleteFileQuietly(a.getStudioFileId());
        }
        a.retry();
        log.info("분석 다시 실행: id={}", id);
    }

    // ------------------------------------------------------------------ 공통

    Analysis load(UUID id) {
        return analyses.findById(id).orElseThrow(ApiException::notFound);
    }

    AnalysisDtos.AnalysisSummary summary(Analysis a, boolean withFileName) {
        String fileName = null;
        if (withFileName) {
            try {
                fileName = encryption.decryptText(a.getOriginalFilenameEnc());
            } catch (RuntimeException e) {
                fileName = "(이름을 읽을 수 없음)";
            }
        }
        return new AnalysisDtos.AnalysisSummary(a.getId(), a.getStatus(), fileName, a.getRiskGrade(),
                a.isGradeProvisional(), a.getReviewRequired(), a.getReviewStatus(), a.getDocumentType(),
                a.getFindingCount(), a.getPageCount(), a.getErrorCode(), a.getCreatedAt(), a.getCompletedAt(),
                StudioService.REPLAY_AGENT_ID.equals(a.getStudioAgentId()));
    }

    /** 검토 대기열 (F08). 자기가 올린 분석은 뺍니다(BR13). */
    @Transactional(readOnly = true)
    public List<AnalysisDtos.AnalysisSummary> reviewQueue(AuthUser user) {
        return analyses.findReviewQueue(user.id()).stream().map(a -> summary(a, false)).toList();
    }

    /** 검토 기록 (F08, BR12~BR14). 분석의 review_status 변경과 검토 저장을 한 트랜잭션에서 합니다. */
    @Transactional
    public void recordReview(AuthUser user, UUID id, Review.Decision decision, String comment) {
        Analysis a = load(id);
        policy.requireView(user, a);
        if (a.getOwnerId().equals(user.id())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "OWN_ANALYSIS", "자기가 올린 분석은 검토할 수 없습니다.");
        }
        if (!policy.canReview(user, a)) {
            throw ApiException.conflict("NOT_PENDING", "검토 대기 중인 분석이 아닙니다.");
        }
        String trimmed = comment == null ? null : comment.strip();
        if (decision == Review.Decision.DISPUTED && (trimmed == null || trimmed.isEmpty())) {
            throw ApiException.badRequest("COMMENT_REQUIRED", "이의를 남기려면 사유를 입력해 주세요.");
        }
        a.recordReview(decision == Review.Decision.CONFIRMED ? ReviewStatus.CONFIRMED : ReviewStatus.DISPUTED);
        reviews.save(Review.create(id, user.id(), decision, trimmed == null || trimmed.isEmpty() ? null : trimmed,
                Instant.now()));
        audit.record(AuditAction.REVIEW_RECORDED, user.id(), "ANALYSIS", id.toString(), null,
                Map.of("decision", decision.name()));
    }

}

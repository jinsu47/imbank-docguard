package com.imbank.docguard.analysis;

import com.imbank.docguard.result.RiskGrade;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 문서 한 건의 분석 (DATABASE.md 3장 analyses, DOMAIN.md 5장 상태 전이).
 * 등급과 사람 검토 여부는 Studio 결과에서만 채웁니다(BR01). 원문값은 여기에 두지 않습니다.
 */
@Entity
@Table(name = "analyses")
public class Analysis {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AnalysisStatus status;

    @Column(name = "original_filename_enc", nullable = false, length = 512)
    private String originalFilenameEnc;

    @Column(name = "stored_path", length = 255)
    private String storedPath;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "file_sha256", nullable = false, length = 64)
    private String fileSha256;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "studio_agent_id", nullable = false, length = 64)
    private String studioAgentId;

    @Column(name = "studio_config_id", nullable = false, length = 64)
    private String studioConfigId;

    @Column(name = "studio_file_id", length = 64)
    private String studioFileId;

    @Column(name = "studio_job_id", length = 64)
    private String studioJobId;

    @Column(name = "document_type", length = 50)
    private String documentType;

    @Column(name = "document_type_confidence", precision = 4, scale = 3)
    private BigDecimal documentTypeConfidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_grade", length = 10)
    private RiskGrade riskGrade;

    @Column(name = "grade_provisional", nullable = false)
    private boolean gradeProvisional;

    @Column(name = "review_required")
    private Boolean reviewRequired;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 20)
    private ReviewStatus reviewStatus;

    @Column(name = "parse_check_verdict", length = 10)
    private String parseCheckVerdict;

    @Column(name = "extract_check_verdict", length = 10)
    private String extractCheckVerdict;

    @Column(name = "finding_count")
    private Integer findingCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "completed_steps", nullable = false)
    private List<String> completedSteps = new ArrayList<>();

    @Column(name = "error_code", length = 40)
    private String errorCode;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "purged_at")
    private Instant purgedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Analysis() {
    }

    public static Analysis create(Long ownerId, String originalFilenameEnc, String storedPath, String sha256, long size,
                                  String contentType, Integer pageCount, String agentId, String configId,
                                  Duration retention, Instant now) {
        Analysis a = new Analysis();
        a.id = UUID.randomUUID();
        a.ownerId = ownerId;
        a.status = AnalysisStatus.UPLOADING;
        a.originalFilenameEnc = originalFilenameEnc;
        a.storedPath = storedPath;
        a.fileSha256 = sha256;
        a.fileSize = size;
        a.contentType = contentType;
        a.pageCount = pageCount;
        a.studioAgentId = agentId;
        a.studioConfigId = configId;
        a.reviewStatus = ReviewStatus.NOT_REQUIRED;
        a.createdAt = now;
        a.updatedAt = now;
        a.expiresAt = now.plus(retention);
        return a;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    // ------------------------------------------------------------ 상태 전이 (DOMAIN.md 5장)

    /** UPLOADING → QUEUED: Studio 업로드·작업 생성 성공 */
    public void markQueued(String fileId, String jobId, Instant now) {
        requireStatus(AnalysisStatus.UPLOADING);
        studioFileId = fileId;
        studioJobId = jobId;
        submittedAt = now;
        status = AnalysisStatus.QUEUED;
    }

    /** QUEUED → RUNNING: Studio in_progress. 끝난 단계 목록을 갱신합니다. */
    public void markRunning(List<String> steps) {
        if (status == AnalysisStatus.QUEUED) {
            status = AnalysisStatus.RUNNING;
        }
        requireStatus(AnalysisStatus.RUNNING);
        completedSteps = new ArrayList<>(steps);
    }

    public void updateSteps(List<String> steps) {
        completedSteps = new ArrayList<>(steps);
    }

    /**
     * QUEUED·RUNNING → COMPLETED: 결과 저장. 사람 검토가 필요하면 검토 대기(BR03).
     */
    public void markCompleted(CompletedResult r, Instant now) {
        if (!(status == AnalysisStatus.QUEUED || status == AnalysisStatus.RUNNING)) {
            throw new IllegalStateException("완료로 바꿀 수 없는 상태입니다: " + status);
        }
        status = AnalysisStatus.COMPLETED;
        completedSteps = new ArrayList<>(r.completedSteps());
        if (r.pageCount() != null) {
            pageCount = r.pageCount();
        }
        documentType = r.documentType();
        documentTypeConfidence = r.documentTypeConfidence();
        riskGrade = r.riskGrade();
        gradeProvisional = r.provisional();
        reviewRequired = r.reviewRequired();
        reviewStatus = r.reviewRequired() ? ReviewStatus.PENDING : ReviewStatus.NOT_REQUIRED;
        parseCheckVerdict = r.parseCheckVerdict();
        extractCheckVerdict = r.extractCheckVerdict();
        findingCount = r.findingCount();
        completedAt = now;
        errorCode = null;
    }

    /** UPLOADING·QUEUED·RUNNING → FAILED */
    public void markFailed(String code, Instant now) {
        if (!status.inProgress()) {
            throw new IllegalStateException("실패로 바꿀 수 없는 상태입니다: " + status);
        }
        status = AnalysisStatus.FAILED;
        errorCode = code;
        completedAt = now;
    }

    /** FAILED → UPLOADING: 다시 실행(소유자) */
    public void retry() {
        requireStatus(AnalysisStatus.FAILED);
        if (storedPath == null) {
            throw new IllegalStateException("원본이 없어 다시 실행할 수 없습니다.");
        }
        status = AnalysisStatus.UPLOADING;
        errorCode = null;
        studioFileId = null;
        studioJobId = null;
        submittedAt = null;
        completedAt = null;
        completedSteps = new ArrayList<>();
    }

    /** Studio 파일을 지웠으면 ID 를 비웁니다(BR07). */
    public void clearStudioFile() {
        studioFileId = null;
    }

    /** COMPLETED·FAILED → PURGED: 원본과 상세 결과를 지우고 요약만 남김(BR15) */
    /** 보관 기간이 지나 관리자에게 삭제를 권고할 분석인지 봅니다(BR15). 권고만 하며 자동으로 지우지 않습니다. */
    public boolean isDeletionRecommended(Instant now) {
        return purgedAt == null && (status == AnalysisStatus.COMPLETED || status == AnalysisStatus.FAILED)
                && expiresAt.isBefore(now);
    }

    /** 검토 결정 기록 (BR12) */
    public void recordReview(ReviewStatus decision) {
        if (reviewStatus != ReviewStatus.PENDING) {
            throw new IllegalStateException("검토 대기 상태가 아닙니다.");
        }
        if (decision != ReviewStatus.CONFIRMED && decision != ReviewStatus.DISPUTED) {
            throw new IllegalArgumentException("검토 결정은 CONFIRMED 또는 DISPUTED 입니다.");
        }
        reviewStatus = decision;
    }

    private void requireStatus(AnalysisStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("상태가 " + expected + " 가 아닙니다: " + status);
        }
    }

    /** 완료 시 Analysis 에 반영할 요약 값 */
    public record CompletedResult(List<String> completedSteps, Integer pageCount, String documentType,
                                  BigDecimal documentTypeConfidence, RiskGrade riskGrade, boolean provisional,
                                  boolean reviewRequired, String parseCheckVerdict, String extractCheckVerdict,
                                  int findingCount) {
    }

    // ------------------------------------------------------------ getter

    public UUID getId() {
        return id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public AnalysisStatus getStatus() {
        return status;
    }

    public String getOriginalFilenameEnc() {
        return originalFilenameEnc;
    }

    public String getStoredPath() {
        return storedPath;
    }

    public String getFileSha256() {
        return fileSha256;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getContentType() {
        return contentType;
    }

    public Integer getPageCount() {
        return pageCount;
    }

    public String getStudioAgentId() {
        return studioAgentId;
    }

    public String getStudioConfigId() {
        return studioConfigId;
    }

    public String getStudioFileId() {
        return studioFileId;
    }

    public String getStudioJobId() {
        return studioJobId;
    }

    public String getDocumentType() {
        return documentType;
    }

    public BigDecimal getDocumentTypeConfidence() {
        return documentTypeConfidence;
    }

    public RiskGrade getRiskGrade() {
        return riskGrade;
    }

    public boolean isGradeProvisional() {
        return gradeProvisional;
    }

    public Boolean getReviewRequired() {
        return reviewRequired;
    }

    public ReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    public String getParseCheckVerdict() {
        return parseCheckVerdict;
    }

    public String getExtractCheckVerdict() {
        return extractCheckVerdict;
    }

    public Integer getFindingCount() {
        return findingCount;
    }

    public List<String> getCompletedSteps() {
        return completedSteps;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getPurgedAt() {
        return purgedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

package com.imbank.docguard.analysis;

import com.imbank.docguard.result.RiskGrade;
import com.imbank.docguard.review.Review;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 분석 API 응답 형식 */
public final class AnalysisDtos {

    private AnalysisDtos() {
    }

    /**
     * 목록 한 줄. 파일 이름은 소유자 목록에서만 채웁니다.
     *
     * @param replay 저장 응답을 돌려주는 재생 모드로 만든 분석인지(실제 판정이 아님)
     */
    public record AnalysisSummary(UUID id, AnalysisStatus status, String fileName, RiskGrade riskGrade,
                                  boolean gradeProvisional, Boolean reviewRequired, ReviewStatus reviewStatus,
                                  String documentType, Integer findingCount, Integer pageCount, String errorCode,
                                  Instant createdAt, Instant completedAt, boolean replay) {
    }

    /**
     * 분석 화면 상단 정보.
     *
     * @param steps          워크플로 단계 순서(진행 표시용)
     * @param completedSteps 끝난 단계
     * @param owner          내가 올린 분석인지
     * @param canManage      삭제·다시 실행을 할 수 있는지(소유자 또는 관리자)
     * @param canReview      지금 검토를 남길 수 있는지
     */
    public record AnalysisDetail(AnalysisSummary summary, String contentType, String studioConfigId,
                                 String parseCheckVerdict, String extractCheckVerdict, List<String> steps,
                                 List<String> completedSteps, Instant submittedAt, Instant expiresAt, Instant purgedAt,
                                 boolean owner, boolean canManage, boolean canReview, ReviewInfo review) {
    }

    public record ReviewInfo(Review.Decision decision, String comment, Instant createdAt) {
    }

    public record CreatedResponse(UUID id) {
    }

    public record RevealRequest(
            @NotBlank(message = "원문을 보는 사유를 입력해 주세요.")
            @Size(min = 2, max = 300, message = "사유는 2자 이상 300자 이하로 입력해 주세요.")
            String reason) {
    }

    public record ReviewRequest(
            @NotNull(message = "검토 결정을 선택해 주세요.") Review.Decision decision,
            @Size(max = 1000, message = "의견은 1,000자 이하로 입력해 주세요.") String comment) {
    }
}

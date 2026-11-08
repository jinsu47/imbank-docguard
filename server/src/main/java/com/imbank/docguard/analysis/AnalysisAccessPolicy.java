package com.imbank.docguard.analysis;

import com.imbank.docguard.auth.AuthUser;
import com.imbank.docguard.common.ApiException;
import com.imbank.docguard.review.ReviewRepository;
import com.imbank.docguard.user.Role;
import org.springframework.stereotype.Component;

/**
 * 분석 단위 접근 확인 (SECURITY.md 1장, 3장). 컨트롤러가 아니라 서비스에서 부릅니다.
 * 볼 수 없는 분석은 403 이 아니라 404 로 응답해 존재 여부를 숨깁니다.
 * <ul>
 *   <li>소유자(INSPECTOR, REVIEWER)는 자기 분석을 봅니다.</li>
 *   <li>REVIEWER 는 검토 대기 중이거나 자기가 검토한 분석을 봅니다("검토 범위").</li>
 *   <li>ADMIN 은 모든 분석을 보고 관리합니다(BR11). 다른 사람 문서의 원본 열람·원문 보기·삭제는 감사 기록에 남습니다.</li>
 * </ul>
 */
@Component
public class AnalysisAccessPolicy {

    private final ReviewRepository reviews;

    public AnalysisAccessPolicy(ReviewRepository reviews) {
        this.reviews = reviews;
    }

    public boolean isOwner(AuthUser user, Analysis analysis) {
        return analysis.getOwnerId().equals(user.id());
    }

    public boolean inReviewScope(AuthUser user, Analysis analysis) {
        if (user.role() != Role.REVIEWER) {
            return false;
        }
        boolean pending = analysis.getStatus() == AnalysisStatus.COMPLETED
                && analysis.getReviewStatus() == ReviewStatus.PENDING;
        return pending || reviews.existsByAnalysisIdAndReviewerId(analysis.getId(), user.id());
    }

    public void requireView(AuthUser user, Analysis analysis) {
        if (user.role() != Role.ADMIN && !isOwner(user, analysis) && !inReviewScope(user, analysis)) {
            throw ApiException.notFound();
        }
    }

    /** 삭제·다시 실행을 할 수 있는지: 소유자 또는 관리자 */
    public boolean canManage(AuthUser user, Analysis analysis) {
        return user.role() == Role.ADMIN || isOwner(user, analysis);
    }

    public void requireManage(AuthUser user, Analysis analysis) {
        if (!canManage(user, analysis)) {
            throw ApiException.notFound();
        }
    }

    /** 검토를 남길 수 있는지 (BR12, BR13) */
    public boolean canReview(AuthUser user, Analysis analysis) {
        return (user.role() == Role.REVIEWER || user.role() == Role.ADMIN)
                && !analysis.getOwnerId().equals(user.id())
                && analysis.getStatus() == AnalysisStatus.COMPLETED
                && analysis.getReviewStatus() == ReviewStatus.PENDING;
    }
}

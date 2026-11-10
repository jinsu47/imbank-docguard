package com.imbank.docguard.analysis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AnalysisRepository extends JpaRepository<Analysis, UUID> {

    /** 내 분석 목록. 항상 owner_id 조건을 넣습니다(SECURITY.md 3장). */
    List<Analysis> findTop100ByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    List<Analysis> findByStatusInOrderByCreatedAtAsc(Collection<AnalysisStatus> statuses);

    List<Analysis> findByStudioFileIdIsNotNullAndStatusIn(Collection<AnalysisStatus> statuses);

    /** 검토 대기열: 자기가 올린 분석은 빼고(BR13) 오래된 순 */
    @Query("select a from Analysis a where a.reviewStatus = com.imbank.docguard.analysis.ReviewStatus.PENDING "
            + "and a.status = com.imbank.docguard.analysis.AnalysisStatus.COMPLETED and a.ownerId <> :reviewerId "
            + "order by a.createdAt asc")
    List<Analysis> findReviewQueue(Long reviewerId);

    /** 보관 기간이 지나 관리자에게 삭제를 권고할 분석 (BR15) */
    @Query("select a from Analysis a where a.purgedAt is null and a.expiresAt < :now and a.status in "
            + "(com.imbank.docguard.analysis.AnalysisStatus.COMPLETED, com.imbank.docguard.analysis.AnalysisStatus.FAILED)")
    List<Analysis> findDeletionRecommended(Instant now);
}

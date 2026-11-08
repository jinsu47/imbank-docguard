package com.imbank.docguard.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByAnalysisId(UUID analysisId);

    boolean existsByAnalysisIdAndReviewerId(UUID analysisId, Long reviewerId);

    @Modifying
    @Query("delete from Review r where r.analysisId = :analysisId")
    void deleteByAnalysisId(UUID analysisId);
}

package com.imbank.docguard.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** 검토 결정 (DATABASE.md 3장 reviews). 분석 하나에 한 번만 기록하고 바꾸지 않습니다(BR12). */
@Entity
@Table(name = "reviews")
public class Review {

    public enum Decision { CONFIRMED, DISPUTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "analysis_id", nullable = false, updatable = false)
    private UUID analysisId;

    @Column(name = "reviewer_id", nullable = false, updatable = false)
    private Long reviewerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 20, updatable = false)
    private Decision decision;

    @Column(name = "comment", columnDefinition = "text", updatable = false)
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Review() {
    }

    public static Review create(UUID analysisId, Long reviewerId, Decision decision, String comment, Instant now) {
        return new Review(analysisId, reviewerId, decision, comment, now);
    }

    private Review(UUID analysisId, Long reviewerId, Decision decision, String comment, Instant now) {
        this.analysisId = analysisId;
        this.reviewerId = reviewerId;
        this.decision = decision;
        this.comment = comment;
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public UUID getAnalysisId() {
        return analysisId;
    }

    public Long getReviewerId() {
        return reviewerId;
    }

    public Decision getDecision() {
        return decision;
    }

    public String getComment() {
        return comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

package com.imbank.docguard.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAnalysisControllerTest {

    private static Analysis at(String createdAt) {
        return Analysis.create(1L, "enc", "path", "sha", 1L, "application/pdf", 1, "agent", "3", Duration.ofDays(7),
                Instant.parse(createdAt));
    }

    @Test
    @DisplayName("BR15: 최근 목록 밖의 권고 문서를 합치고, 겹치는 문서는 한 번만 넣으며, 최신순을 유지한다")
    void mergesRecommendedOutsideRecentList() {
        Analysis oldest = at("2026-10-01T00:00:00Z");
        Analysis middle = at("2026-11-01T00:00:00Z");
        Analysis newest = at("2026-11-09T00:00:00Z");

        assertThat(AdminAnalysisController.withRecommended(List.of(newest, middle), List.of(oldest, middle)))
                .containsExactly(newest, middle, oldest);
    }
}

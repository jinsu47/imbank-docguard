package com.imbank.docguard.analysis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 주기 작업. 테스트는 app.scheduling.enabled=false 로 끄고 처리기를 직접 부릅니다.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class SchedulingConfig {

    private final AnalysisJobProcessor processor;

    SchedulingConfig(AnalysisJobProcessor processor) {
        this.processor = processor;
    }

    /** Studio 작업 상태 조회 (UPSTAGE_POLL_INTERVAL_MS, 기본 3초) */
    @Scheduled(fixedDelayString = "${upstage.poll-interval-ms:3000}", initialDelay = 2000)
    void pollStudio() {
        processor.processAll();
    }
}

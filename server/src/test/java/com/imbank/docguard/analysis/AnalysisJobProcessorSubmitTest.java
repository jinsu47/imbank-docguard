package com.imbank.docguard.analysis;

import com.imbank.docguard.result.AnalysisResultService;
import com.imbank.docguard.storage.EncryptionService;
import com.imbank.docguard.storage.FileStorage;
import com.imbank.docguard.studio.StudioException;
import com.imbank.docguard.studio.StudioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Studio 업로드 실패 처리. 동시 진행 건수를 제한하지 않으므로(BR06), 한꺼번에 올린 문서가 Studio 의 429 로
 * 바로 실패하지 않고 제한 시간까지 기다리는지 확인합니다.
 */
class AnalysisJobProcessorSubmitTest {

    private static final long TIMEOUT_SEC = 600;

    private final AnalysisRepository analyses = mock(AnalysisRepository.class);
    private final StudioService studio = mock(StudioService.class);
    private final FileStorage storage = mock(FileStorage.class);
    private AnalysisJobProcessor processor;

    @BeforeEach
    void setUp() {
        TransactionTemplate tx = new TransactionTemplate() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(new SimpleTransactionStatus());
            }
        };
        processor = new AnalysisJobProcessor(analyses, studio, mock(AnalysisResultService.class), storage,
                mock(EncryptionService.class), tx);
        when(storage.read(any())).thenReturn(new byte[]{1});
        when(studio.jobTimeoutSec(any())).thenReturn(TIMEOUT_SEC);
    }

    private Analysis uploading(long ageSeconds) {
        Analysis a = Analysis.create(1L, "enc", "stored.bin", "sha", 1, "application/pdf", 1, "agent", "2",
                Duration.ofHours(1), Instant.now().minusSeconds(ageSeconds));
        when(analyses.findByStatusInOrderByCreatedAtAsc(any())).thenReturn(List.of(a));
        when(analyses.findById(a.getId())).thenReturn(Optional.of(a));
        return a;
    }

    private void studioRejects(int status, boolean retryable) {
        when(studio.submit(any(), any(), any()))
                .thenThrow(new StudioException("STUDIO_HTTP_" + status, retryable, "실패"));
    }

    @Test
    @DisplayName("429 는 횟수와 상관없이 제한 시간까지 다시 시도한다")
    void retriesRateLimitUntilTimeout() {
        Analysis a = uploading(10);
        studioRejects(429, true);

        for (int i = 0; i < 10; i++) {
            processor.processAll();
        }

        assertThat(a.getStatus()).isEqualTo(AnalysisStatus.UPLOADING);
    }

    @Test
    @DisplayName("제한 시간이 지나도 429 이면 실패로 바꾼다")
    void failsAfterTimeout() {
        Analysis a = uploading(TIMEOUT_SEC + 10);
        studioRejects(429, true);

        processor.processAll();

        assertThat(a.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(a.getErrorCode()).isEqualTo("STUDIO_UPLOAD_FAILED");
    }

    @Test
    @DisplayName("다시 시도해도 소용없는 오류는 바로 실패로 바꾼다")
    void failsImmediatelyWhenNotRetryable() {
        Analysis a = uploading(10);
        studioRejects(403, false);

        processor.processAll();

        assertThat(a.getStatus()).isEqualTo(AnalysisStatus.FAILED);
    }
}

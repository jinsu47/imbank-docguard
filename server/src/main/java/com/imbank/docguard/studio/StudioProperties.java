package com.imbank.docguard.studio;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code upstage.*} 설정 (ARCHITECTURE.md 4장, 7장).
 *
 * @param mode           live: 실제 Upstage API 호출, replay: 저장해 둔 실제 응답을 돌려줌(로컬 개발 전용)
 * @param apiKey         비밀 값. 로그·응답에 남기지 않습니다.
 * @param projectId      모든 요청의 x-project-id 헤더
 * @param agentId        v2.5 워크플로 Agent ID
 * @param configId       설정 버전. 빼면 최신 설정이 실행되므로 반드시 보냅니다.
 * @param replayDir      replay 모드가 읽는 {@code *_raw.json} 폴더
 * @param replayStepMs   replay 모드에서 단계 하나가 끝나는 데 걸리는 시간(진행 표시 시험용). 테스트는 0
 * @param steps          Studio 노드 이름. 코드에 박지 않고 설정으로 둡니다.
 */
@ConfigurationProperties(prefix = "upstage")
public record StudioProperties(
        Mode mode,
        String apiKey,
        String projectId,
        String agentId,
        String configId,
        String baseUrl,
        long pollIntervalMs,
        long jobTimeoutSec,
        String replayDir,
        long replayStepMs,
        Steps steps) {

    public enum Mode { LIVE, REPLAY }

    public record Steps(
            String parse,
            String parseCheck,
            String classify,
            String extract,
            String match,
            String extractCheck,
            String report) {
    }

    public boolean liveConfigured() {
        return notBlank(apiKey) && notBlank(projectId) && notBlank(agentId) && notBlank(configId);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}

package com.imbank.docguard.studio;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;

/** UPSTAGE_MODE 에 따라 실제 호출 또는 재생 통로를 고릅니다. */
@Configuration
class StudioConfig {

    private static final Logger log = LoggerFactory.getLogger(StudioConfig.class);

    @Bean
    StudioGateway studioGateway(StudioProperties properties, RestClient.Builder builder, ObjectMapper objectMapper) {
        if (properties.mode() == StudioProperties.Mode.REPLAY) {
            log.warn("Studio 재생 모드입니다. Upstage 를 호출하지 않고 저장해 둔 응답을 돌려줍니다(로컬 개발 전용).");
            return new ReplayStudioGateway(properties.replayDir(), properties.replayStepMs(), objectMapper, Clock.systemUTC());
        }
        if (!properties.liveConfigured()) {
            log.warn("Upstage 설정이 비어 있습니다. 분석을 만들면 STUDIO_NOT_CONFIGURED 로 실패합니다. .env 를 확인하세요.");
        }
        var settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(10))
                .withReadTimeout(Duration.ofSeconds(60));
        return new StudioHttpClient(properties,
                builder.requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings)), objectMapper);
    }
}

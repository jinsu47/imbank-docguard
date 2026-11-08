package com.imbank.docguard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 금융문서 민감정보 점검 웹 서비스 백엔드 진입점.
 * 판정은 Upstage Studio 워크플로가 내리고, 이 서버는 결과를 해석·저장·표시합니다. (ARCHITECTURE.md 1장)
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class DocguardApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocguardApplication.class, args);
    }
}

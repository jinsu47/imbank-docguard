package com.imbank.docguard.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * 배포용 jar 에 들어 있는 React 빌드(classpath:/static)를 제공합니다 (ARCHITECTURE.md 6장).
 * /analyses/… 처럼 화면 경로로 바로 들어와도 index.html 을 돌려줘 React Router 가 화면을 고릅니다.
 * API 경로와 확장자가 있는 파일은 없으면 404 입니다. 로컬 개발(Vite)에서는 static 폴더가 없어 아무 일도 하지 않습니다.
 */
@Configuration
class SpaResourceConfig implements WebMvcConfigurer {

    private static final String INDEX = "index.html";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(false)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String path, Resource location) throws IOException {
                        Resource found = super.getResource(path, location);
                        if (found != null || !isScreenPath(path)) {
                            return found;
                        }
                        return super.getResource(INDEX, location);
                    }
                });
    }

    static boolean isScreenPath(String path) {
        return !path.startsWith("api/") && !path.startsWith("actuator/") && !path.contains(".");
    }
}

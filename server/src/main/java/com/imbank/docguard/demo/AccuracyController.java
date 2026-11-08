package com.imbank.docguard.demo;

import com.imbank.docguard.auth.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 시연 정확도 (F12). 로그인한 사용자 자신의 실행 결과만 비교합니다. */
@RestController
@RequestMapping("/api/demo")
public class AccuracyController {

    private final AccuracyService service;

    public AccuracyController(AccuracyService service) {
        this.service = service;
    }

    @GetMapping("/accuracy")
    public AccuracyService.Report accuracy() {
        return service.report(CurrentUser.require());
    }
}

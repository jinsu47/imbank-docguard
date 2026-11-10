package com.imbank.docguard.review;

import com.imbank.docguard.analysis.AnalysisDtos;
import com.imbank.docguard.analysis.AnalysisService;
import com.imbank.docguard.auth.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 검토 대기열 (F08). 검토 결정 기록은 {@code POST /api/analyses/{id}/review}입니다. */
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final AnalysisService analyses;

    public ReviewController(AnalysisService analyses) {
        this.analyses = analyses;
    }

    @GetMapping("/queue")
    public List<AnalysisDtos.AnalysisSummary> queue() {
        return analyses.reviewQueue(CurrentUser.require());
    }
}

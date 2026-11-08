package com.imbank.docguard.studio;

import com.imbank.docguard.common.Point;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@link StudioResultMapper}가 Studio 응답에서 꺼낸 서비스 객체들. 다른 패키지는 Studio JSON 대신 이것만 씁니다.
 */
public final class StudioModels {

    private StudioModels() {
    }

    public enum JobStatus { QUEUED, IN_PROGRESS, COMPLETED, FAILED }

    /** 워크플로 단계. 진행 표시와 completed_steps 에 이 이름을 씁니다. */
    public enum Step { PARSE, PARSE_CHECK, CLASSIFY, EXTRACT, MATCH, EXTRACT_CHECK, REPORT }

    /** 작업 조회 결과. outcome 은 완료일 때만 있습니다. */
    public record JobSnapshot(JobStatus status, List<Step> completedSteps, Outcome outcome) {
    }

    /** 완료된 작업의 결과 */
    public record Outcome(
            Integer pageCount,
            String parseCheckVerdict,
            String extractCheckVerdict,
            String documentType,
            BigDecimal documentTypeConfidence,
            List<ExtractedFinding> findings,
            String reportText,
            List<Citation> citations,
            List<StepRun> steps) {
    }

    /**
     * Extract 단계의 {@code 민감정보 발견내역[]} 한 행.
     * rawValue, evidence, subjectBasis, reviewReason 은 원문이 들어 있으므로 암호화해서만 저장합니다.
     */
    public record ExtractedFinding(
            int rowIndex,
            String rawValue,
            String itemLabel,
            String infoType,
            String subjectRole,
            String subjectBasis,
            String evidence,
            String maskState,
            String valueNature,
            String reviewReason,
            boolean policyLinked,
            Integer page,
            List<List<Point>> wordBoxes,
            List<Point> elementBox,
            String confidence) {
    }

    /** 보고서의 【†n】 하나. sourceRef 가 발견 항목을 가리키면 rowIndex 와 field 가 채워집니다. */
    public record Citation(
            int index,
            String sourceRef,
            Integer rowIndex,
            String field,
            Integer page,
            List<List<Point>> wordBoxes,
            List<Point> elementBox) {
    }

    public record StepRun(Step step, String name, boolean cacheHit) {
    }

    public record Submission(String fileId, String jobId) {
    }
}

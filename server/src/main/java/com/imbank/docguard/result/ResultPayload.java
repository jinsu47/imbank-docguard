package com.imbank.docguard.result;

import com.imbank.docguard.studio.StudioModels;

import java.util.List;
import java.util.Map;

/**
 * analysis_results.encrypted_payload 에 암호화해 두는 JSON (DATABASE.md 3장).
 * 원문값, 원문 근거, 정보주체 식별근거, 검토 사유, 잔존 정보, 보고서 본문이 들어 있으므로 평문으로 저장하거나 로그에 남기지 않습니다.
 * gradeRoute 와 residualInfo 는 v4 부터 있어, 이전에 저장한 결과에서는 null 입니다.
 */
record ResultPayload(Report report, List<RawFinding> findings, List<StudioModels.Citation> citations,
                     List<StudioModels.StepRun> steps, StudioModels.GradeRoute gradeRoute) {

    record Report(String raw, Map<String, String> sections, String reviewReason) {
    }

    record RawFinding(int rowIndex, String rawValue, String evidence, String subjectBasis, String reviewReason,
                      String residualInfo) {
    }
}

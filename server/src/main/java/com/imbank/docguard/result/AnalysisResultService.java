package com.imbank.docguard.result;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imbank.docguard.storage.EncryptionService;
import com.imbank.docguard.studio.StudioModels.ExtractedFinding;
import com.imbank.docguard.studio.StudioModels.Outcome;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 결과 해석과 저장 (DOMAIN.md 3장 "결과 해석" 컨텍스트). 판정을 새로 내리지 않고 보고서에서 읽기만 합니다(BR01, BR02).
 */
@Service
public class AnalysisResultService {

    private final AnalysisResultRecordRepository records;
    private final FindingRepository findings;
    private final EncryptionService encryption;
    private final ObjectMapper objectMapper;

    public AnalysisResultService(AnalysisResultRecordRepository records, FindingRepository findings,
                                 EncryptionService encryption, ObjectMapper objectMapper) {
        this.records = records;
        this.findings = findings;
        this.encryption = encryption;
        this.objectMapper = objectMapper;
    }

    /** 보고서에서 읽은 판정 요약 */
    public record Verdict(RiskGrade grade, boolean provisional, boolean reviewRequired, int findingCount,
                          boolean reportParsed) {
    }

    /**
     * Studio 결과를 저장하고 보고서에서 읽은 판정을 돌려줍니다. 호출하는 쪽의 트랜잭션 안에서 실행됩니다.
     */
    @Transactional
    public Verdict store(UUID analysisId, Outcome outcome, Instant now) {
        ReportParser.ParsedReport report = ReportParser.parse(outcome.reportText());

        findings.deleteByAnalysisId(analysisId);
        List<ResultPayload.RawFinding> rawFindings = new ArrayList<>();
        for (ExtractedFinding f : outcome.findings()) {
            findings.save(new Finding(analysisId, f.rowIndex(), blankTo(f.itemLabel(), "(항목명 없음)"),
                    blankTo(f.infoType(), "판별불가"), blankTo(f.subjectRole(), "판별불가"),
                    blankTo(f.maskState(), "판별불가"), emptyToNull(f.valueNature()), f.policyLinked(), f.page(),
                    f.wordBoxes(), f.elementBox(), f.confidence()));
            rawFindings.add(new ResultPayload.RawFinding(f.rowIndex(), f.rawValue(), f.evidence(), f.subjectBasis(),
                    f.reviewReason(), f.residualInfo()));
        }
        ResultPayload payload = new ResultPayload(
                new ResultPayload.Report(outcome.reportText(), report.sections(), report.reviewReason()),
                rawFindings, outcome.citations(), outcome.steps(), outcome.gradeRoute());
        records.save(new AnalysisResultRecord(analysisId, encryption.encrypt(toJson(payload)),
                EncryptionService.KEY_VERSION, now));
        return new Verdict(report.grade(), report.provisional(), report.reviewRequired(), outcome.findings().size(),
                report.parsed());
    }

    @Transactional(readOnly = true)
    public ResultDtos.ResultView view(UUID analysisId) {
        ResultPayload payload = load(analysisId);
        Map<Integer, ResultPayload.RawFinding> raw = new java.util.HashMap<>();
        payload.findings().forEach(f -> raw.put(f.rowIndex(), f));

        List<ResultDtos.FindingView> views = findings.findByAnalysisIdOrderByRowIndexAsc(analysisId).stream()
                .map(f -> new ResultDtos.FindingView(f.getRowIndex(), f.getItemLabel(), f.getInfoType(),
                        f.getSubjectRole(), f.getMaskState(), f.getValueNature(), f.isPolicyLinked(),
                        DisplayValueMasker.mask(raw.containsKey(f.getRowIndex()) ? raw.get(f.getRowIndex()).rawValue() : "",
                                f.getInfoType(), f.getMaskState()),
                        raw.containsKey(f.getRowIndex())
                                ? DisplayValueMasker.residual(raw.get(f.getRowIndex()).residualInfo(), f.getMaskState())
                                : null,
                        f.getPage(), f.getWordBoxes(), f.getElementBox(), f.getConfidence()))
                .toList();

        List<ResultDtos.Section> sections = new ArrayList<>();
        Map<String, String> bySection = displaySections(payload.report());
        for (String title : ReportParser.SECTION_TITLES) {
            if (bySection.containsKey(title)) {
                sections.add(new ResultDtos.Section(title, ReportDisplayText.of(bySection.get(title))));
            }
        }
        List<ResultDtos.CitationView> citations = payload.citations() == null ? List.of() : payload.citations().stream()
                .map(c -> new ResultDtos.CitationView(c.index(), c.rowIndex(), c.field(), c.page(), c.wordBoxes(),
                        c.elementBox()))
                .toList();
        return new ResultDtos.ResultView(sections, ReportDisplayText.of(payload.report().reviewReason()), views, citations,
                payload.gradeRoute());
    }

    /** 저장 원문에서 다시 읽어, 제목 반복 처리 전에 저장된 결과도 문단이 모두 보이게 합니다. */
    static Map<String, String> displaySections(ResultPayload.Report report) {
        if (report.raw() != null && !report.raw().isBlank()) {
            return ReportParser.parse(report.raw()).sections();
        }
        return report.sections() == null ? Map.of() : report.sections();
    }

    @Transactional(readOnly = true)
    public List<ResultDtos.RevealedFinding> reveal(UUID analysisId) {
        return load(analysisId).findings().stream()
                .map(f -> new ResultDtos.RevealedFinding(f.rowIndex(), f.rawValue(), f.evidence(), f.subjectBasis(),
                        ReportDisplayText.of(f.reviewReason())))
                .toList();
    }

    public boolean exists(UUID analysisId) {
        return records.existsById(analysisId);
    }

    /** 원본과 같은 시점에 상세 결과와 발견 항목을 지웁니다(BR15, BR16). */
    @Transactional
    public void delete(UUID analysisId) {
        findings.deleteByAnalysisId(analysisId);
        records.findById(analysisId).ifPresent(records::delete);
    }

    private ResultPayload load(UUID analysisId) {
        AnalysisResultRecord record = records.findById(analysisId)
                .orElseThrow(() -> new IllegalStateException("분석 결과가 없습니다."));
        try {
            return objectMapper.readValue(encryption.decrypt(record.getEncryptedPayload()), ResultPayload.class);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("분석 결과를 읽지 못했습니다.");
        }
    }

    private byte[] toJson(ResultPayload payload) {
        try {
            return objectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("분석 결과를 직렬화하지 못했습니다.");
        }
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

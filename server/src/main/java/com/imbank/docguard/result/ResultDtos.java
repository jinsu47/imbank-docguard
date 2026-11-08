package com.imbank.docguard.result;

import com.imbank.docguard.common.Point;

import java.util.List;

/** 결과 화면 응답 형식 (F03~F06). 원문값은 넣지 않고 표시값만 넣습니다(BR08). */
public final class ResultDtos {

    private ResultDtos() {
    }

    /**
     * @param sections     보고서 항목. 문단 안의 【†n】 표시는 남겨 두고 화면이 인용 링크로 바꿉니다.
     * @param reviewReason "사람 검토: 필요." 뒤의 사유 문장
     */
    public record ResultView(List<Section> sections, String reviewReason, List<FindingView> findings,
                             List<CitationView> citations) {
    }

    public record Section(String title, String body) {
    }

    public record FindingView(int rowIndex, String itemLabel, String infoType, String subjectRole, String maskState,
                              String valueNature, boolean policyLinked, String displayValue, Integer page,
                              List<List<Point>> wordBoxes, List<Point> elementBox, String confidence) {
    }

    public record CitationView(int index, Integer rowIndex, String field, Integer page, List<List<Point>> wordBoxes,
                               List<Point> elementBox) {
    }

    /** 원문 보기 응답. 사유를 적고 감사 기록을 남긴 뒤에만 돌려줍니다(BR08). */
    public record RevealedFinding(int rowIndex, String rawValue, String evidence, String subjectBasis,
                                  String reviewReason) {
    }
}

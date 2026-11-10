package com.imbank.docguard.result;

import com.imbank.docguard.common.Point;
import com.imbank.docguard.studio.StudioModels;

import java.util.List;

/** 결과 화면 응답 형식 (F03~F06). 원문값은 넣지 않고 표시값만 넣습니다(BR08). */
public final class ResultDtos {

    private ResultDtos() {
    }

    /**
     * @param sections     보고서 항목. 문단 안의 【†n】 표시는 남겨 두고 화면이 인용 링크로 바꿉니다.
     * @param reviewReason "사람 검토: 필요." 뒤의 사유 문장
     * @param gradeRoute   등급이 정해진 경로. v4 이전 결과에는 없습니다.
     */
    public record ResultView(List<Section> sections, String reviewReason, List<FindingView> findings,
                             List<CitationView> citations, StudioModels.GradeRoute gradeRoute) {
    }

    public record Section(String title, String body) {
    }

    /** @param residualInfo 일부 가린 항목에 남은 정보의 종류. 그 밖의 항목에는 없습니다. */
    public record FindingView(int rowIndex, String itemLabel, String infoType, String subjectRole, String maskState,
                              String valueNature, boolean policyLinked, String displayValue, String residualInfo,
                              Integer page, List<List<Point>> wordBoxes, List<Point> elementBox, String confidence) {
    }

    public record CitationView(int index, Integer rowIndex, String field, Integer page, List<List<Point>> wordBoxes,
                               List<Point> elementBox) {
    }

    /** 원문 보기 응답. 사유를 적고 감사 기록을 남긴 뒤에만 돌려줍니다(BR08). */
    public record RevealedFinding(int rowIndex, String rawValue, String evidence, String subjectBasis,
                                  String reviewReason) {
    }
}

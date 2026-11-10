package com.imbank.docguard.result;

import com.imbank.docguard.common.Point;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

/**
 * 발견 항목 (DATABASE.md 3장 findings). 원문값 없이 목록과 문서 위 상자에 필요한 값만 둡니다.
 * 원문값은 rowIndex 로 암호화 결과의 findings 와 연결합니다.
 */
@Entity
@Table(name = "findings")
public class Finding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "analysis_id", nullable = false, updatable = false)
    private UUID analysisId;

    @Column(name = "row_index", nullable = false)
    private int rowIndex;

    @Column(name = "item_label", nullable = false, length = 100)
    private String itemLabel;

    @Column(name = "info_type", nullable = false, length = 30)
    private String infoType;

    @Column(name = "subject_role", nullable = false, length = 30)
    private String subjectRole;

    @Column(name = "mask_state", nullable = false, length = 20)
    private String maskState;

    @Column(name = "value_nature", length = 20)
    private String valueNature;

    @Column(name = "policy_linked", nullable = false)
    private boolean policyLinked;

    @Column(name = "page")
    private Integer page;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "word_boxes")
    private List<List<Point>> wordBoxes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "element_box")
    private List<Point> elementBox;

    @Column(name = "confidence", length = 10)
    private String confidence;

    protected Finding() {
    }

    Finding(UUID analysisId, int rowIndex, String itemLabel, String infoType, String subjectRole, String maskState,
            String valueNature, boolean policyLinked, Integer page, List<List<Point>> wordBoxes, List<Point> elementBox,
            String confidence) {
        this.analysisId = analysisId;
        this.rowIndex = rowIndex;
        this.itemLabel = cut(itemLabel, 100);
        this.infoType = cut(infoType, 30);
        this.subjectRole = cut(subjectRole, 30);
        this.maskState = cut(maskState, 20);
        this.valueNature = cut(valueNature, 20);
        this.policyLinked = policyLinked;
        this.page = page;
        this.wordBoxes = wordBoxes;
        this.elementBox = elementBox;
        this.confidence = cut(confidence, 10);
    }

    private static String cut(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    public Long getId() {
        return id;
    }

    public UUID getAnalysisId() {
        return analysisId;
    }

    public int getRowIndex() {
        return rowIndex;
    }

    public String getItemLabel() {
        return itemLabel;
    }

    public String getInfoType() {
        return infoType;
    }

    public String getSubjectRole() {
        return subjectRole;
    }

    public String getMaskState() {
        return maskState;
    }

    public String getValueNature() {
        return valueNature;
    }

    public boolean isPolicyLinked() {
        return policyLinked;
    }

    public Integer getPage() {
        return page;
    }

    public List<List<Point>> getWordBoxes() {
        return wordBoxes;
    }

    public List<Point> getElementBox() {
        return elementBox;
    }

    public String getConfidence() {
        return confidence;
    }
}

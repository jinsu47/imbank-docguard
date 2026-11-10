package com.imbank.docguard.analysis;

import java.util.EnumSet;
import java.util.Set;

/** 분석 상태 (DOMAIN.md 5장) */
public enum AnalysisStatus {
    UPLOADING,
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    PURGED;

    /** 진행 중 상태. 사용자당 최대 3건(BR06) */
    public static final Set<AnalysisStatus> IN_PROGRESS = EnumSet.of(UPLOADING, QUEUED, RUNNING);

    public boolean inProgress() {
        return IN_PROGRESS.contains(this);
    }
}

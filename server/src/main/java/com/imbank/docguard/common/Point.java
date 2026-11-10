package com.imbank.docguard.common;

/** 문서 위의 상대 좌표(0~1). 화면이 렌더링된 페이지 크기를 곱해 그립니다. */
public record Point(double x, double y) {
}

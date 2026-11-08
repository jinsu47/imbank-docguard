package com.imbank.docguard.demo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccuracyServiceTest {

    @Test
    @DisplayName("파일 이름의 태그를 찾을 때 set1 이 set10 에 걸리지 않는다")
    void containsTag() {
        assertThat(AccuracyService.containsTag("금융보안원_미션2_set02.pdf", "set02")).isTrue();
        assertThat(AccuracyService.containsTag("금융보안원_미션2_set10.pdf", "set1")).isFalse();
        assertThat(AccuracyService.containsTag("set1_set10.pdf", "set1")).isTrue();
        assertThat(AccuracyService.containsTag("other.pdf", "set02")).isFalse();
    }
}

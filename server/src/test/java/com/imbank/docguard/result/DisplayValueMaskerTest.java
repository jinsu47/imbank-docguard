package com.imbank.docguard.result;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** 표시값 규칙 (BR09). 값은 모두 시험용으로 만든 가짜 값입니다. */
class DisplayValueMaskerTest {

    @ParameterizedTest(name = "{1}: {0} → {3}")
    @CsvSource(delimiter = '|', value = {
            "홍길동|이름|미마스킹|홍**",
            "900101-1234567|주민등록번호|미마스킹|900101-*******",
            "M12345678|여권번호|미마스킹|M1*******",
            "123-45-678901-2|계좌번호|미마스킹|***-**-***901-2",
            "1234-5678-9012-3456|카드번호|미마스킹|****-****-****-3456",
            "010-1234-5678|전화번호|미마스킹|***-****-5678",
            "abcdef@example.com|이메일|미마스킹|ab***@example.com",
            "대구광역시 수성구 범어로 1|주소|미마스킹|대구광역시 수성구 ***",
            "2,486,500원|예금잔액|미마스킹|***,***원",
            "당뇨|건강정보|미마스킹|(가림)",
            "900101-1******|주민등록번호|부분마스킹|900101-1******",
    })
    @DisplayName("정보 유형별 표시값")
    void masks(String raw, String infoType, String maskState, String expected) {
        assertThat(DisplayValueMasker.mask(raw, infoType, maskState)).isEqualTo(expected);
    }
}

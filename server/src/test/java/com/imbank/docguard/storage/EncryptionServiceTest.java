package com.imbank.docguard.storage;

import com.imbank.docguard.TestFixtures;
import com.imbank.docguard.common.AppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** AES-256-GCM 저장 형식 (DATABASE.md 1장) */
class EncryptionServiceTest {

    private static EncryptionService service(String key) {
        return new EncryptionService(new AppProperties(key, "storage", 168, false));
    }

    @Test
    @DisplayName("암호화 결과는 IV 12바이트 + 암호문 + 태그 16바이트이고, 같은 값도 매번 다르게 암호화된다")
    void roundTrip() {
        EncryptionService service = service(TestFixtures.encryptionKey());
        byte[] plain = "예금주 시험값".getBytes(StandardCharsets.UTF_8);

        byte[] a = service.encrypt(plain);
        byte[] b = service.encrypt(plain);

        assertThat(a).hasSize(12 + plain.length + 16);
        assertThat(a).isNotEqualTo(b);
        assertThat(service.decrypt(a)).isEqualTo(plain);
        assertThat(service.decryptText(service.encryptText("파일.pdf"))).isEqualTo("파일.pdf");
    }

    @Test
    @DisplayName("다른 키나 바뀐 암호문은 복호화하지 못한다")
    void tamperAndWrongKey() {
        EncryptionService service = service(TestFixtures.encryptionKey());
        byte[] data = service.encrypt("값".getBytes(StandardCharsets.UTF_8));
        byte[] tampered = data.clone();
        tampered[tampered.length - 1] ^= 1;

        assertThatThrownBy(() -> service.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service(TestFixtures.encryptionKey()).decrypt(data)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("키가 없거나 32바이트가 아니면 서버가 시작되지 않는다(임시 키를 쓰지 않음)")
    void requiresKey() {
        assertThatThrownBy(() -> service("")).isInstanceOf(IllegalStateException.class).hasMessageContaining("APP_ENCRYPTION_KEY");
        assertThatThrownBy(() -> service("c2hvcnQ=")).isInstanceOf(IllegalStateException.class);
    }
}

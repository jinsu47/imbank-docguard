package com.imbank.docguard.storage;

import com.imbank.docguard.common.AppProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 암호화 (DATABASE.md 1장, SECURITY.md 5장).
 * 결과 형식은 {@code 12바이트 IV + 암호문 + 16바이트 태그}입니다.
 * 키는 {@code APP_ENCRYPTION_KEY}(32바이트 Base64)에서만 읽고, 없으면 서버가 시작되지 않습니다.
 */
@Service
public class EncryptionService {

    /** 키를 바꿀 때 올립니다. 저장한 행의 key_version 과 비교해 어느 키로 풀지 정합니다. */
    public static final short KEY_VERSION = 1;

    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public EncryptionService(AppProperties properties) {
        this.key = parseKey(properties.encryptionKey());
    }

    static SecretKey parseKey(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException(
                    "APP_ENCRYPTION_KEY 가 없습니다. IM-BANK 디렉터리에서 scripts/create-env 로 .env 를 만드세요.");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("APP_ENCRYPTION_KEY 가 Base64 형식이 아닙니다.");
        }
        if (bytes.length != 32) {
            throw new IllegalStateException("APP_ENCRYPTION_KEY 는 32바이트여야 합니다.");
        }
        return new SecretKeySpec(bytes, "AES");
    }

    public byte[] encrypt(byte[] plain) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain);
            return ByteBuffer.allocate(IV_LENGTH + encrypted.length).put(iv).put(encrypted).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("암호화에 실패했습니다.", e);
        }
    }

    public byte[] decrypt(byte[] data) {
        if (data == null || data.length < IV_LENGTH + TAG_BITS / 8) {
            throw new IllegalStateException("암호문 형식이 올바르지 않습니다.");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, data, 0, IV_LENGTH));
            return cipher.doFinal(data, IV_LENGTH, data.length - IV_LENGTH);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("복호화에 실패했습니다. 암호화 키가 바뀌었을 수 있습니다.", e);
        }
    }

    /** 문자열을 암호화해 Base64 로 돌려줍니다. 파일 이름처럼 varchar 컬럼에 넣을 때 씁니다. */
    public String encryptText(String plain) {
        return Base64.getEncoder().encodeToString(encrypt(plain.getBytes(StandardCharsets.UTF_8)));
    }

    public String decryptText(String base64) {
        return new String(decrypt(Base64.getDecoder().decode(base64)), StandardCharsets.UTF_8);
    }
}

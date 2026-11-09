package com.imbank.docguard;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 테스트 자료 위치와 테스트용 값 생성.
 * 평가 자료(eval/)는 대회 규정 확인 전까지 공개 저장소에 올리지 않으므로, 없으면 해당 테스트를 건너뜁니다.
 * 비밀 값은 실행할 때마다 무작위로 만듭니다(저장소에 임시 값도 두지 않음).
 */
public final class TestFixtures {

    /** Gradle 테스트의 작업 디렉터리는 server/ 입니다. */
    public static final Path EVAL = Path.of("..", "eval").toAbsolutePath().normalize();
    public static final Path API_RAW_DIR = EVAL.resolve("testmdfile/api-20261106-111210");
    public static final Path V25_REPORTS = EVAL.resolve("testmdfile/v2.5");
    public static final Path ANSWER_KEY = EVAL.resolve("answer_key.json");
    public static final Path ANSWER_KEY_V3 = EVAL.resolve("answer_key_v3.json");
    public static final Path V32_REPORTS = EVAL.resolveSibling("studio").resolve("benchmark/v3.2");

    private static final SecureRandom RANDOM = new SecureRandom();

    private TestFixtures() {
    }

    public static Path requireEval(Path path) {
        assumeTrue(Files.exists(path), "평가 자료가 없어 건너뜁니다: " + path);
        return path;
    }

    public static Path rawResponse(String tag) throws IOException {
        requireEval(API_RAW_DIR);
        try (var files = Files.list(API_RAW_DIR)) {
            return files.filter(p -> p.getFileName().toString().contains(tag) && p.getFileName().toString().endsWith("_raw.json"))
                    .findFirst().orElseThrow();
        }
    }

    public static String encryptionKey() {
        byte[] key = new byte[32];
        RANDOM.nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    /** 가입 규칙(10자 이상, 영문·숫자)을 만족하는 무작위 비밀번호 */
    public static String password() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        return "pw" + HexFormat.of().formatHex(bytes) + "7";
    }

    /** 빈 쪽으로 된 PDF */
    public static byte[] pdf(int pages) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                doc.addPage(new PDPage());
            }
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

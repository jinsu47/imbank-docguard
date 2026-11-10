package com.imbank.docguard.analysis;

import com.imbank.docguard.common.ApiException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 업로드 검증 (BR05, SECURITY.md 4장). 형식은 확장자나 Content-Type 이 아니라 파일 앞부분의 시그니처로 정합니다.
 * 파일 크기와 쪽수는 제한하지 않습니다. 쪽수는 제한 시간 계산과 기록에만 씁니다.
 */
@Component
public class UploadValidator {

    public record ValidatedFile(String contentType, Integer pageCount, String sha256) {
    }

    public ValidatedFile validate(byte[] content) {
        if (content == null || content.length == 0) {
            throw ApiException.badRequest("EMPTY_FILE", "빈 파일입니다.");
        }
        String contentType = detect(content);
        if (contentType == null) {
            throw ApiException.badRequest("UNSUPPORTED_FILE", "PDF, JPG, PNG 파일만 올릴 수 있습니다.");
        }
        Integer pages = "application/pdf".equals(contentType) ? pdfPages(content) : Integer.valueOf(1);
        return new ValidatedFile(contentType, pages, sha256(content));
    }

    static String detect(byte[] b) {
        if (b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') {
            return "application/pdf";
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        return null;
    }

    private int pdfPages(byte[] content) {
        try (PDDocument doc = Loader.loadPDF(content)) {
            if (doc.isEncrypted()) {
                throw ApiException.badRequest("ENCRYPTED_PDF", "암호가 걸린 PDF 는 올릴 수 없습니다.");
            }
            int pages = doc.getNumberOfPages();
            if (pages < 1) {
                throw ApiException.badRequest("INVALID_PDF", "PDF 를 읽을 수 없습니다.");
            }
            return pages;
        } catch (InvalidPasswordException e) {
            throw ApiException.badRequest("ENCRYPTED_PDF", "암호가 걸린 PDF 는 올릴 수 없습니다.");
        } catch (IOException e) {
            throw ApiException.badRequest("INVALID_PDF", "PDF 를 읽을 수 없습니다.");
        }
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

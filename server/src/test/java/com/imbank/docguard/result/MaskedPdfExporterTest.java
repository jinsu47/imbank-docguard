package com.imbank.docguard.result;

import com.imbank.docguard.common.Point;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MaskedPdfExporterTest {

    private static final String SECRET = "900101-1234567";
    private static final String KEEP = "public heading";

    private final MaskedPdfExporter exporter = new MaskedPdfExporter();

    private static byte[] samplePdf() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            doc.getDocumentInformation().setTitle("홍길동 주민등록번호");
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream c = new PDPageContentStream(doc, page)) {
                c.beginText();
                c.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 14);
                c.newLineAtOffset(100, 700);
                c.showText(SECRET);
                c.newLineAtOffset(0, -100);
                c.showText(KEEP);
                c.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    /** A4 위의 PDF 좌표(왼쪽 아래 원점)를 0~1 상대 좌표(왼쪽 위 원점) 상자로 바꿉니다. */
    private static List<Point> box(float left, float bottom, float right, float top) {
        float w = PDRectangle.A4.getWidth();
        float h = PDRectangle.A4.getHeight();
        return List.of(new Point(left / w, (h - top) / h), new Point(right / w, (h - top) / h),
                new Point(right / w, (h - bottom) / h), new Point(left / w, (h - bottom) / h));
    }

    private static ResultDtos.FindingView finding(String maskState, String display, Integer page, List<List<Point>> words,
                                                  List<Point> element) {
        return new ResultDtos.FindingView(0, "주민등록번호", "주민등록번호", "신청인", maskState, null, true, display, null, page,
                words, element, "high");
    }

    private static boolean isBlack(int rgb) {
        return (rgb & 0xFFFFFF) == 0;
    }

    @Test
    @DisplayName("F13: 값 자리를 칠하고 글자 층과 문서 정보를 없앤다")
    void masksValueAndRemovesTextLayer() throws Exception {
        List<Point> valueBox = box(95, 692, 240, 716);

        MaskedPdfExporter.Output output = exporter.render(samplePdf(),
                List.of(finding("미마스킹", "900101-*******", 1, List.of(valueBox), null)));

        assertThat(output.areaMasked()).isZero();
        assertThat(output.unlocated()).isZero();
        try (PDDocument result = Loader.loadPDF(output.content())) {
            assertThat(result.getNumberOfPages()).isEqualTo(1);
            assertThat(new PDFTextStripper().getText(result)).as("글자 층이 남아 있음").isBlank();
            assertThat(result.getDocumentInformation().getTitle()).isNull();

            BufferedImage page = new PDFRenderer(result).renderImage(0);
            int centerX = (int) ((95 + 240) / 2f / PDRectangle.A4.getWidth() * page.getWidth());
            int centerY = (int) ((PDRectangle.A4.getHeight() - 704) / PDRectangle.A4.getHeight() * page.getHeight());
            assertThat(isBlack(page.getRGB(centerX, centerY))).as("값 자리가 칠해지지 않음").isTrue();
            assertThat(isBlack(page.getRGB(10, 10))).as("다른 자리까지 칠해짐").isFalse();
        }
        assertThat(new String(output.content(), java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain(SECRET);
    }

    @Test
    @DisplayName("F13: 가릴 항목이 없어도 글자 층 없는 PDF 를 만들고 원래 내용은 그림으로 남는다")
    void keepsOtherContentAsImage() throws Exception {
        MaskedPdfExporter.Output output = exporter.render(samplePdf(), List.of());

        try (PDDocument result = Loader.loadPDF(output.content())) {
            assertThat(new PDFTextStripper().getText(result)).isBlank();
            BufferedImage page = new PDFRenderer(result).renderImageWithDPI(0, 200);
            int dark = 0;
            for (int x = 0; x < page.getWidth(); x++) {
                for (int y = 0; y < page.getHeight(); y++) {
                    if ((page.getRGB(x, y) & 0xFF) < 128) {
                        dark++;
                    }
                }
            }
            assertThat(dark).as("원래 글자가 그림으로 남아야 함").isGreaterThan(100);
        }
    }

    @Test
    @DisplayName("F13: 이미 전체 가림인 값은 칠하지 않고, 값 위치가 없으면 영역 전체를 칠하며, 위치를 모르면 세어 알린다(BR10)")
    void plan() {
        List<Point> b = box(95, 692, 240, 716);
        MaskedPdfExporter.Plan plan = MaskedPdfExporter.plan(List.of(
                finding("전체마스킹", "*****", 1, List.of(b), null),
                finding("부분마스킹", "900101-1******", 1, List.of(b), null),
                finding("미마스킹", "(가림)", 1, null, b),
                finding("미마스킹", "(가림)", null, List.of(b), null),
                finding("미마스킹", "(가림)", 9, List.of(b), null),
                finding("미마스킹", "(가림)", 1, List.of(), null),
                finding("미마스킹", "", 1, List.of(), null)), 1);

        assertThat(plan.boxesByPage().get(0)).hasSize(2);
        assertThat(plan.areaMasked()).isEqualTo(1);
        assertThat(plan.unlocated()).isEqualTo(3);
    }
}

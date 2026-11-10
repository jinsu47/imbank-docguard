package com.imbank.docguard.result;

import com.imbank.docguard.common.ApiException;
import com.imbank.docguard.common.Point;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 가린 PDF 만들기 (F13). 각 쪽을 이미지로 그린 뒤 값 자리를 칠해 새 PDF 로 만듭니다.
 * 원본 PDF 위에 상자만 얹으면 글자 층이 남아 복사로 값을 꺼낼 수 있으므로, 새 PDF 에는 칠해진 이미지만 넣습니다.
 * 글자 층, 메타데이터, 첨부가 함께 사라지고 글자를 선택할 수 없게 됩니다.
 */
@Component
public class MaskedPdfExporter {

    private static final float DPI = 200f;
    private static final float JPEG_QUALITY = 0.8f;
    /** 글자 가장자리가 비치지 않도록 칠하는 영역을 넓히는 양 */
    private static final int PADDING_PX = 2;
    /** 쪽마다 이미지를 만들어 메모리와 CPU 를 많이 쓰므로 동시에 만드는 수를 제한합니다. */
    private final Semaphore slots = new Semaphore(2);

    /**
     * @param areaMasked 값 위치가 없어 영역 전체를 가린 항목 수
     * @param unlocated  위치를 몰라 가리지 못한 항목 수
     */
    public record Output(byte[] content, int areaMasked, int unlocated) {
    }

    record Plan(Map<Integer, List<List<Point>>> boxesByPage, int areaMasked, int unlocated) {
    }

    public Output render(byte[] pdf, List<ResultDtos.FindingView> findings) {
        try {
            if (!slots.tryAcquire(60, TimeUnit.SECONDS)) {
                throw ApiException.tooManyRequests("가린 PDF 요청이 많습니다. 잠시 후 다시 시도해 주세요.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ApiException.tooManyRequests("가린 PDF 요청이 많습니다. 잠시 후 다시 시도해 주세요.");
        }
        try (PDDocument source = Loader.loadPDF(pdf); PDDocument output = new PDDocument()) {
            Plan plan = plan(findings, source.getNumberOfPages());
            PDFRenderer renderer = new PDFRenderer(source);
            for (int i = 0; i < source.getNumberOfPages(); i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, DPI, ImageType.RGB);
                paint(image, plan.boxesByPage().getOrDefault(i, List.of()));
                addPage(output, image);
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            output.save(bytes);
            return new Output(bytes.toByteArray(), plan.areaMasked(), plan.unlocated());
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "MASKED_PDF_FAILED", "가린 PDF 를 만들지 못했습니다.");
        } finally {
            slots.release();
        }
    }

    /**
     * 칠할 자리를 정합니다. 화면의 가림 상자(BR10)와 같게 값 상자를 칠하고, 문서에 이미 전체 가림인 값은 건너뜁니다.
     * 화면과 달리 값 위치 없이 영역만 있는 항목은 그 영역 전체를 칠합니다. 파일은 밖으로 나가므로 값이 남는 것보다 더 가리는 편을 택합니다.
     */
    static Plan plan(List<ResultDtos.FindingView> findings, int pageCount) {
        Map<Integer, List<List<Point>>> byPage = new HashMap<>();
        int areaMasked = 0;
        int unlocated = 0;
        for (ResultDtos.FindingView f : findings) {
            if ("전체마스킹".equals(f.maskState())) {
                continue;
            }
            List<List<Point>> words = drawable(f.wordBoxes());
            List<List<Point>> area = f.elementBox() == null ? List.of() : drawable(List.of(f.elementBox()));
            boolean onPage = f.page() != null && f.page() >= 1 && f.page() <= pageCount;
            if (!onPage || (words.isEmpty() && area.isEmpty())) {
                // 값이 없는 항목(빈칸)은 가릴 것이 없으므로 세지 않습니다.
                if (f.displayValue() != null && !f.displayValue().isBlank()) {
                    unlocated++;
                }
                continue;
            }
            if (words.isEmpty()) {
                areaMasked++;
            }
            byPage.computeIfAbsent(f.page() - 1, p -> new ArrayList<>()).addAll(words.isEmpty() ? area : words);
        }
        return new Plan(byPage, areaMasked, unlocated);
    }

    private static List<List<Point>> drawable(List<List<Point>> boxes) {
        if (boxes == null) {
            return List.of();
        }
        return boxes.stream().filter(MaskedPdfExporter::hasArea).toList();
    }

    private static boolean hasArea(List<Point> box) {
        if (box == null || box.isEmpty()) {
            return false;
        }
        double width = box.stream().mapToDouble(Point::x).max().orElse(0) - box.stream().mapToDouble(Point::x).min().orElse(0);
        double height = box.stream().mapToDouble(Point::y).max().orElse(0) - box.stream().mapToDouble(Point::y).min().orElse(0);
        return width > 0 && height > 0;
    }

    private static void paint(BufferedImage image, List<List<Point>> boxes) {
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.BLACK);
            for (List<Point> box : boxes) {
                int left = (int) Math.floor(minX(box) * image.getWidth()) - PADDING_PX;
                int top = (int) Math.floor(minY(box) * image.getHeight()) - PADDING_PX;
                int right = (int) Math.ceil(maxX(box) * image.getWidth()) + PADDING_PX;
                int bottom = (int) Math.ceil(maxY(box) * image.getHeight()) + PADDING_PX;
                graphics.fillRect(left, top, right - left, bottom - top);
            }
        } finally {
            graphics.dispose();
        }
    }

    private static double minX(List<Point> box) {
        return box.stream().mapToDouble(Point::x).min().orElse(0);
    }

    private static double maxX(List<Point> box) {
        return box.stream().mapToDouble(Point::x).max().orElse(0);
    }

    private static double minY(List<Point> box) {
        return box.stream().mapToDouble(Point::y).min().orElse(0);
    }

    private static double maxY(List<Point> box) {
        return box.stream().mapToDouble(Point::y).max().orElse(0);
    }

    private static void addPage(PDDocument output, BufferedImage image) throws IOException {
        float width = image.getWidth() * 72f / DPI;
        float height = image.getHeight() * 72f / DPI;
        PDPage page = new PDPage(new PDRectangle(width, height));
        output.addPage(page);
        PDImageXObject picture = JPEGFactory.createFromImage(output, image, JPEG_QUALITY);
        try (PDPageContentStream content = new PDPageContentStream(output, page)) {
            content.drawImage(picture, 0, 0, width, height);
        }
    }
}

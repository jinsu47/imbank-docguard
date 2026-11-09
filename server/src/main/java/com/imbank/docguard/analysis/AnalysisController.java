package com.imbank.docguard.analysis;

import com.imbank.docguard.auth.CurrentUser;
import com.imbank.docguard.common.ApiException;
import com.imbank.docguard.result.ResultDtos;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/** 분석 API (ARCHITECTURE.md 5장). 권한 확인은 서비스가 합니다. */
@RestController
@RequestMapping("/api/analyses")
public class AnalysisController {

    private final AnalysisService service;

    public AnalysisController(AnalysisService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AnalysisDtos.CreatedResponse create(@RequestPart("file") MultipartFile file) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("UPLOAD_FAILED", "파일을 읽지 못했습니다.");
        }
        return new AnalysisDtos.CreatedResponse(service.create(CurrentUser.require(), file.getOriginalFilename(), content));
    }

    @GetMapping
    public List<AnalysisDtos.AnalysisSummary> list() {
        return service.listMine(CurrentUser.require());
    }

    @GetMapping("/{id}")
    public AnalysisDtos.AnalysisDetail detail(@PathVariable UUID id) {
        return service.detail(CurrentUser.require(), id);
    }

    @GetMapping("/{id}/result")
    public ResponseEntity<ResultDtos.ResultView> result(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.result(CurrentUser.require(), id));
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable UUID id, @RequestParam(defaultValue = "md") String format) {
        AnalysisService.ExportFile file = service.export(CurrentUser.require(), id, format);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(file.contentType()))
                // 파일 이름은 헤더에 넣지 않고 화면에서 정합니다.
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment");
        if (file.areaMasked() != null) {
            response.header("X-Area-Masked-Count", file.areaMasked().toString())
                    .header("X-Unlocated-Count", file.unlocated().toString());
        }
        return response.body(file.content());
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> file(@PathVariable UUID id, HttpSession session) {
        AnalysisService.FileContent file = service.file(CurrentUser.require(), id, session);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(file.contentType()))
                // 파일 이름은 헤더에 넣지 않습니다.
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(file.content());
    }

    @PostMapping("/{id}/reveal")
    public ResponseEntity<List<ResultDtos.RevealedFinding>> reveal(@PathVariable UUID id,
                                                                   @Valid @RequestBody AnalysisDtos.RevealRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.reveal(CurrentUser.require(), id, request.reason()));
    }

    @PostMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void retry(@PathVariable UUID id) {
        service.retry(CurrentUser.require(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(CurrentUser.require(), id);
    }

    @PostMapping("/{id}/review")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void review(@PathVariable UUID id, @Valid @RequestBody AnalysisDtos.ReviewRequest request) {
        service.recordReview(CurrentUser.require(), id, request.decision(), request.comment());
    }
}

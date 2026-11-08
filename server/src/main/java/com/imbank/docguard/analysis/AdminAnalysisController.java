package com.imbank.docguard.analysis;

import com.imbank.docguard.result.RiskGrade;
import com.imbank.docguard.storage.EncryptionService;
import com.imbank.docguard.studio.StudioService;
import com.imbank.docguard.user.User;
import com.imbank.docguard.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 관리자용 전체 분석 목록 (BR11). 관리자는 모든 분석을 열어 보고 지울 수 있습니다.
 */
@RestController
@RequestMapping("/api/admin/analyses")
public class AdminAnalysisController {

    private final AnalysisRepository analyses;
    private final UserRepository users;
    private final EncryptionService encryption;

    public AdminAnalysisController(AnalysisRepository analyses, UserRepository users, EncryptionService encryption) {
        this.analyses = analyses;
        this.users = users;
        this.encryption = encryption;
    }

    public record AnalysisMeta(UUID id, String fileName, Long ownerId, String ownerEmail, AnalysisStatus status,
                               RiskGrade riskGrade, ReviewStatus reviewStatus, String errorCode, Instant createdAt,
                               Instant completedAt, Instant expiresAt, Instant purgedAt, boolean replay) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<AnalysisMeta> list() {
        List<Analysis> page = analyses.findAll(PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();
        Map<Long, String> emails = users.findAllById(page.stream().map(Analysis::getOwnerId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, User::getEmail, (a, b) -> a));
        return page.stream()
                .map(a -> new AnalysisMeta(a.getId(), fileName(a), a.getOwnerId(), emails.get(a.getOwnerId()),
                        a.getStatus(), a.getRiskGrade(), a.getReviewStatus(), a.getErrorCode(), a.getCreatedAt(),
                        a.getCompletedAt(), a.getExpiresAt(), a.getPurgedAt(),
                        StudioService.REPLAY_AGENT_ID.equals(a.getStudioAgentId())))
                .toList();
    }

    private String fileName(Analysis a) {
        try {
            return encryption.decryptText(a.getOriginalFilenameEnc());
        } catch (RuntimeException e) {
            return "(이름을 읽을 수 없음)";
        }
    }
}

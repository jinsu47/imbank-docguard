package com.imbank.docguard.studio;

import com.imbank.docguard.studio.StudioModels.JobSnapshot;
import com.imbank.docguard.studio.StudioModels.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 다른 패키지가 Studio 를 쓰는 유일한 입구입니다. 응답 JSON 대신 {@link StudioModels}의 객체만 돌려줍니다.
 */
@Service
public class StudioService {

    /** 재생 모드로 만든 분석의 studio_agent_id 값. 화면이 "재생 모드" 표시를 붙이는 기준입니다. */
    public static final String REPLAY_AGENT_ID = "REPLAY";

    private static final Logger log = LoggerFactory.getLogger(StudioService.class);

    private final StudioGateway gateway;
    private final StudioResultMapper mapper;
    private final StudioProperties properties;

    StudioService(StudioGateway gateway, StudioResultMapper mapper, StudioProperties properties) {
        this.gateway = gateway;
        this.mapper = mapper;
        this.properties = properties;
    }

    public boolean replayMode() {
        return properties.mode() == StudioProperties.Mode.REPLAY;
    }

    /** 이 분석에 기록할 Agent ID (BR04) */
    public String agentIdForRecord() {
        if (replayMode()) {
            return REPLAY_AGENT_ID;
        }
        return properties.agentId() == null || properties.agentId().isBlank() ? "-" : properties.agentId();
    }

    /** 이 분석에 기록할 Config ID (BR04) */
    public String configIdForRecord() {
        if (replayMode()) {
            return "replay";
        }
        return properties.configId() == null || properties.configId().isBlank() ? "-" : properties.configId();
    }

    public long pollIntervalMs() {
        return properties.pollIntervalMs();
    }

    public long jobTimeoutSec() {
        return properties.jobTimeoutSec();
    }

    /**
     * 파일을 올리고 작업을 만듭니다. 작업 생성이 실패하면 올린 파일을 지우고 예외를 다시 던집니다.
     */
    public Submission submit(byte[] content, String contentType, String nameHint) {
        String fileId = gateway.uploadFile(content, contentType, nameHint);
        try {
            return new Submission(fileId, gateway.createJob(fileId));
        } catch (RuntimeException e) {
            deleteFileQuietly(fileId);
            throw e;
        }
    }

    /** 파일만 올립니다. 작업 생성을 다시 시도할 때 씁니다. */
    public String upload(byte[] content, String contentType, String nameHint) {
        return gateway.uploadFile(content, contentType, nameHint);
    }

    public String createJob(String fileId) {
        return gateway.createJob(fileId);
    }

    public JobSnapshot poll(String jobId) {
        return mapper.map(gateway.getJob(jobId));
    }

    /** Studio 에 올린 파일을 지웁니다(BR07). 실패해도 예외를 던지지 않고 false 를 돌려줍니다. */
    public boolean deleteFileQuietly(String fileId) {
        if (fileId == null) {
            return true;
        }
        try {
            gateway.deleteFile(fileId);
            return true;
        } catch (StudioException e) {
            log.warn("Studio 파일 삭제 실패: {}", e.code());
            return false;
        }
    }
}

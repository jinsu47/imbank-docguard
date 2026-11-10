package com.imbank.docguard.studio;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Upstage Agents API 를 부르는 통로. 응답 JSON 은 이 패키지 안에서만 다룹니다.
 * 구현은 실제 호출({@link StudioHttpClient})과 저장 응답 재생({@link ReplayStudioGateway}) 두 가지입니다.
 */
interface StudioGateway {

    /**
     * 파일을 올리고 Studio 파일 ID 를 돌려줍니다.
     *
     * @param nameHint 원래 파일 이름. 실제 호출에서는 보내지 않고(고객 이름이 들어 있을 수 있음), 재생 모드가 응답을 고를 때만 씁니다.
     */
    String uploadFile(byte[] content, String contentType, String nameHint);

    /** 작업을 만들고 작업 ID 를 돌려줍니다. */
    String createJob(String fileId);

    /** 작업 상태와 단계별 결과를 조회합니다(include[]=all). */
    JsonNode getJob(String jobId);

    void deleteFile(String fileId);
}

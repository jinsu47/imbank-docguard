package com.imbank.docguard.studio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Upstage Agents API 실제 호출 (ARCHITECTURE.md 4장 호출 순서).
 * <ol>
 *   <li>{@code POST /files} (multipart: file, purpose=user_data)</li>
 *   <li>{@code POST /responses} {model, config_id, include:["last"], input:[...input_file]}</li>
 *   <li>{@code GET /responses/{id}?include[]=all}</li>
 *   <li>{@code DELETE /files/{id}}</li>
 * </ol>
 * 모든 요청에 Bearer 키와 {@code x-project-id}를 붙입니다. 응답 본문과 키는 로그에 남기지 않습니다.
 */
class StudioHttpClient implements StudioGateway {

    private static final Logger log = LoggerFactory.getLogger(StudioHttpClient.class);

    private final StudioProperties properties;
    private final RestClient client;
    private final ObjectMapper objectMapper;

    StudioHttpClient(StudioProperties properties, RestClient.Builder builder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.client = builder
                .baseUrl(properties.baseUrl())
                .defaultHeaders(headers -> {
                    if (properties.liveConfigured()) {
                        headers.setBearerAuth(properties.apiKey());
                        headers.set("x-project-id", properties.projectId());
                    }
                })
                .build();
    }

    @Override
    public String uploadFile(byte[] content, String contentType, String nameHint) {
        requireConfigured();
        // 고객 이름이 들어 있을 수 있는 원래 파일 이름 대신 중립적인 이름을 보냅니다.
        String filename = "document" + extensionOf(contentType);
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("purpose", "user_data");
        parts.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        JsonNode body = call("파일 업로드", () -> client.post()
                .uri("/files")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve()
                .body(JsonNode.class));
        return requireText(body, "id");
    }

    @Override
    public String createJob(String fileId) {
        requireConfigured();
        Map<String, Object> request = Map.of(
                "model", properties.agentId(),
                "config_id", properties.configId(),
                "include", List.of("last"),
                "input", List.of(Map.of(
                        "role", "user",
                        "content", List.of(Map.of("type", "input_file", "file_id", fileId)))));
        JsonNode body = call("작업 생성", () -> client.post()
                .uri("/responses")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(JsonNode.class));
        return requireText(body, "id");
    }

    @Override
    public JsonNode getJob(String jobId) {
        requireConfigured();
        JsonNode body = call("작업 조회", () -> client.get()
                .uri(uri -> uri.path("/responses/{id}").queryParam("include[]", "all").build(jobId))
                .retrieve()
                .body(JsonNode.class));
        if (body == null || !body.hasNonNull("status")) {
            throw new StudioException("STUDIO_BAD_RESPONSE", false, "작업 조회 응답에 status 가 없습니다.");
        }
        return body;
    }

    @Override
    public void deleteFile(String fileId) {
        requireConfigured();
        call("파일 삭제", () -> client.delete()
                .uri("/files/{id}", fileId)
                .retrieve()
                .toBodilessEntity());
    }

    private void requireConfigured() {
        if (!properties.liveConfigured()) {
            throw new StudioException("STUDIO_NOT_CONFIGURED", false,
                    "UPSTAGE_API_KEY, UPSTAGE_PROJECT_ID, UPSTAGE_AGENT_ID, UPSTAGE_CONFIG_ID 중 비어 있는 값이 있습니다.");
        }
    }

    private <T> T call(String action, Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String errorCode = errorCodeOf(e.getResponseBodyAsByteArray());
            // 오류 메시지 원문에는 파일 정보가 들어갈 수 있으므로 상태 코드와 code 값만 남깁니다.
            log.warn("Studio {} 실패: HTTP {} code={}", action, status, errorCode);
            boolean retryable = status == 429 || status >= 500;
            throw new StudioException("STUDIO_HTTP_" + status, retryable, action + " 실패: HTTP " + status);
        } catch (ResourceAccessException e) {
            log.warn("Studio {} 실패: 네트워크 오류 {}", action, e.getClass().getSimpleName());
            throw new StudioException("STUDIO_NETWORK", true, action + " 실패: 네트워크 오류");
        } catch (RestClientException e) {
            log.warn("Studio {} 실패: 응답 처리 오류 {}", action, e.getClass().getSimpleName());
            throw new StudioException("STUDIO_BAD_RESPONSE", false, action + " 실패: 응답 형식 오류");
        }
    }

    private String errorCodeOf(byte[] body) {
        if (body == null || body.length == 0) {
            return "-";
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            JsonNode code = node.path("error").path("code");
            if (code.isMissingNode() || code.isNull()) {
                code = node.path("code");
            }
            return code.isValueNode() ? code.asText() : "-";
        } catch (Exception e) {
            return "-";
        }
    }

    private static String requireText(JsonNode body, String field) {
        if (body == null || !body.hasNonNull(field) || body.get(field).asText().isBlank()) {
            throw new StudioException("STUDIO_BAD_RESPONSE", false, "응답에 " + field + " 가 없습니다.");
        }
        return body.get(field).asText();
    }

    static String extensionOf(String contentType) {
        return switch (contentType) {
            case "application/pdf" -> ".pdf";
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            default -> ".bin";
        };
    }
}

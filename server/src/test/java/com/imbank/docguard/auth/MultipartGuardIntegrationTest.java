package com.imbank.docguard.auth;

import com.imbank.docguard.IntegrationTestBase;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 업로드 크기를 제한하지 않으므로, 로그인하지 않은 요청의 multipart 본문이 서버 디스크에 저장되면 안 됩니다.
 * 파일 저장은 인증을 통과한 뒤 DispatcherServlet 에서만 일어납니다.
 */
class MultipartGuardIntegrationTest extends IntegrationTestBase {

    private static MockMultipartHttpServletRequestBuilder upload(String path) {
        return multipart(path).file(new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[]{1}));
    }

    @Test
    @DisplayName("업로드 주소가 아니면 multipart 를 본문을 읽기 전에 415 로 거절한다")
    void rejectsMultipartOutsideUpload() throws Exception {
        mockMvc.perform(upload("/api/auth/login").with(csrfHeader()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("MULTIPART_NOT_ALLOWED"));
        mockMvc.perform(upload("/"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @DisplayName("업로드에 CSRF 헤더가 없으면 403 으로 거절한다")
    void uploadWithoutHeaderIsForbidden() throws Exception {
        mockMvc.perform(upload("/api/analyses"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    @DisplayName("CSRF 값이 맞아도 로그인하지 않았으면 401 로 거절한다")
    void uploadWithoutLoginIsUnauthorized() throws Exception {
        mockMvc.perform(upload("/api/analyses").with(csrfHeader()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("multipart 에 CSRF 헤더가 없으면 요청 파라미터를 읽지 않는다(읽으면 본문이 디스크에 저장됨)")
    void doesNotReadParametersOfMultipart() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContentType()).thenReturn("multipart/form-data; boundary=x");
        CsrfToken token = new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "token");

        assertThat(new SpaCsrfTokenRequestHandler().resolveCsrfTokenValue(request, token)).isNull();

        verify(request, never()).getParameter(any());
        verify(request, never()).getParts();
    }
}

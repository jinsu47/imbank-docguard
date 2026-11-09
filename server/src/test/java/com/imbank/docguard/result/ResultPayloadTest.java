package com.imbank.docguard.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResultPayloadTest {

    @Test
    @DisplayName("v4 이전에 저장한 결과도 읽고, 없는 값은 null 로 둔다")
    void readsPayloadSavedBeforeV4() throws Exception {
        String saved = """
                {"report":{"raw":"보고서","sections":{},"reviewReason":null},
                 "findings":[{"rowIndex":0,"rawValue":"값","evidence":"근거","subjectBasis":"식별","reviewReason":"사유"}],
                 "citations":[],"steps":[]}
                """;

        ResultPayload payload = new ObjectMapper().readValue(saved, ResultPayload.class);

        assertThat(payload.gradeRoute()).isNull();
        assertThat(payload.findings().get(0).rawValue()).isEqualTo("값");
        assertThat(payload.findings().get(0).residualInfo()).isNull();
    }
}

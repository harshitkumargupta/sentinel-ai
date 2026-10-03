package com.sentinelai.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.ingestion.normalize.AuthLogNormalizer;
import com.sentinelai.ingestion.normalize.GenericJsonNormalizer;
import com.sentinelai.ingestion.normalize.NormalizedEvent;
import com.sentinelai.ingestion.normalize.WebAccessLogNormalizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void genericHandlesMalformedInputWithDefaults() throws Exception {
        // Unknown enum values and missing fields must not throw.
        var raw = mapper.readTree("{\"eventType\":\"NONSENSE\",\"severity\":\"???\",\"sourceIp\":\"1.2.3.4\"}");
        NormalizedEvent n = new GenericJsonNormalizer().normalize(raw);
        assertThat(n.getEventType()).isEqualTo(EventType.OTHER);
        assertThat(n.getSeverity()).isEqualTo(Severity.LOW);
        assertThat(n.getSourceIp()).isEqualTo("1.2.3.4");
    }

    @Test
    void genericHandlesEmptyObject() throws Exception {
        NormalizedEvent n = new GenericJsonNormalizer().normalize(mapper.readTree("{}"));
        assertThat(n.getEventType()).isEqualTo(EventType.OTHER);
        assertThat(n.getEventTimestamp()).isNull();
    }

    @Test
    void authLogMapsSuccessAndFailure() throws Exception {
        var fail = mapper.readTree("{\"username\":\"u\",\"sourceIp\":\"1.1.1.1\",\"success\":false}");
        assertThat(new AuthLogNormalizer().normalize(fail).getEventType()).isEqualTo(EventType.FAILED_LOGIN);

        var ok = mapper.readTree("{\"username\":\"u\",\"success\":true}");
        assertThat(new AuthLogNormalizer().normalize(ok).getEventType()).isEqualTo(EventType.SUSPICIOUS_LOGIN);
    }

    @Test
    void webAccessMapsErrorsToApiAbuse() throws Exception {
        var err = mapper.readTree("{\"sourceIp\":\"2.2.2.2\",\"path\":\"/x\",\"status\":503}");
        assertThat(new WebAccessLogNormalizer().normalize(err).getEventType()).isEqualTo(EventType.API_ABUSE);

        var ok = mapper.readTree("{\"sourceIp\":\"2.2.2.2\",\"path\":\"/x\",\"status\":200}");
        assertThat(new WebAccessLogNormalizer().normalize(ok).getEventType()).isEqualTo(EventType.OTHER);
    }
}

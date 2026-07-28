package dev.kage.server.gateway.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.LoggingEvent;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class JsonLogEncoderTest {

    private static final Logger LOGGER = (Logger) LoggerFactory.getLogger(JsonLogEncoderTest.class);

    @Test
    void shouldProduceValidJsonLine_withRequiredFields() {
        LoggingEvent event = new LoggingEvent(
                JsonLogEncoderTest.class.getName(), LOGGER, Level.INFO, "Đăng nhập thành công", null, null);

        String json = new String(new JsonLogEncoder().encode(event), StandardCharsets.UTF_8);

        assertThat(json).contains("\"level\":\"INFO\"");
        assertThat(json).contains("\"logger\":\"" + JsonLogEncoderTest.class.getName() + "\"");
        assertThat(json).contains("\"message\":\"Đăng nhập thành công\"");
        assertThat(json).endsWith("}" + System.lineSeparator());
    }

    @Test
    void shouldEscapeQuotesAndNewlines_inMessage() {
        LoggingEvent event = new LoggingEvent(
                JsonLogEncoderTest.class.getName(), LOGGER, Level.WARN,
                "line1\nline2 \"quoted\" \\backslash", null, null);

        String json = new String(new JsonLogEncoder().encode(event), StandardCharsets.UTF_8);

        assertThat(json).contains("line1\\nline2 \\\"quoted\\\" \\\\backslash");
        assertThat(json).doesNotContain("\n\"level\"");
    }

    @Test
    void shouldIncludeStackTrace_whenThrowablePresent() {
        LoggingEvent event = new LoggingEvent(
                JsonLogEncoderTest.class.getName(), LOGGER, Level.ERROR, "lỗi không mong đợi",
                new IllegalStateException("boom"), null);

        String json = new String(new JsonLogEncoder().encode(event), StandardCharsets.UTF_8);

        assertThat(json).contains("\"stack_trace\"");
        assertThat(json).contains("IllegalStateException");
    }
}

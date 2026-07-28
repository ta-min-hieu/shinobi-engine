package dev.kage.server.gateway.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.encoder.EncoderBase;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Encoder JSON tự viết cho Logback — OBSERVABILITY.md §5.1 "Production: JSON structured — máy
 * parse được". KHÔNG dùng {@code logstash-logback-encoder} (thư viện ngoài không có trong
 * TECHNOLOGY-STACK.md, thêm vào cần hỏi trước — CLAUDE.md §4); input JSON ở đây chỉ vài field
 * string/số CỐ ĐỊNH (không phải object đồ thị tuỳ ý), nên tự escape đủ dùng là đủ, không cần thư
 * viện serialize JSON tổng quát.
 *
 * <p>Kích hoạt qua {@code -Dlogback.configurationFile=logback-production.xml} lúc chạy production
 * (xem {@code logback-production.xml} cùng thư mục resources) — {@code ./gradlew run} (dev) không
 * set flag này nên vẫn dùng cấu hình mặc định (console human-readable) của Logback.
 */
public final class JsonLogEncoder extends EncoderBase<ILoggingEvent> {

    @Override
    public byte[] headerBytes() {
        return new byte[0];
    }

    @Override
    public byte[] encode(ILoggingEvent event) {
        StringBuilder json = new StringBuilder(256);
        json.append('{');
        appendField(json, "timestamp", Instant.ofEpochMilli(event.getTimeStamp()).toString());
        json.append(',');
        appendField(json, "level", event.getLevel().toString());
        json.append(',');
        appendField(json, "logger", event.getLoggerName());
        json.append(',');
        appendField(json, "thread", event.getThreadName());
        json.append(',');
        appendField(json, "message", event.getFormattedMessage());
        IThrowableProxy throwableProxy = event.getThrowableProxy();
        if (throwableProxy != null) {
            json.append(',');
            appendField(json, "stack_trace", ThrowableProxyUtil.asString(throwableProxy));
        }
        json.append('}').append(System.lineSeparator());
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] footerBytes() {
        return new byte[0];
    }

    private static void appendField(StringBuilder json, String key, String value) {
        json.append('"').append(key).append("\":\"").append(escape(value)).append('"');
    }

    /** Đủ cho JSON hợp lệ (RFC 8259 §7) — không cần thư viện: input chỉ text log tự do, không markup lồng nhau. */
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }
}

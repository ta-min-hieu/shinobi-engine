package dev.kage.tools.loadtest;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Đếm lỗi TÁCH THEO LOẠI (2026-10-03). Bản cũ cộng mọi thứ vào một {@code errorCount} duy nhất —
 * bắt tay hỏng, hết giờ chờ, mất kết nối, server từ chối có lý do — nên một con số "N lỗi" không
 * trả lời được câu hỏi capacity nào (BENCHMARK.md §5.1 "Công cụ đo đã HỎNG THEO HAI CÁCH").
 */
final class FailureCounters {

    /** Loại lỗi — mỗi loại một câu hỏi khác nhau cho người đọc báo cáo. */
    enum Kind {
        /** Không vào được world: TLS/Connect/ListCharacters/Create/Select hỏng hoặc bị từ chối. */
        HANDSHAKE,
        /** Hết {@code SO_TIMEOUT} khi chờ phản hồi — tín hiệu quá tải điển hình. */
        TIMEOUT,
        /** Đọc hết ngân sách frame mà không thấy phản hồi — server bận đẩy sự kiện hoặc bỏ quên. */
        RESPONSE_MISSING,
        /** Socket chết / frame hỏng giữa lúc chơi. */
        CONNECTION_LOST,
        /** Server TRẢ LỜI bằng {@code ErrorResponse} — không phải quá tải, là từ chối có lý do. */
        REJECTED
    }

    // Dựng đủ mọi khoá một lần trong constructor, sau đó chỉ đọc ⇒ an toàn đa luồng không cần khoá.
    private final Map<Kind, AtomicLong> counts = new EnumMap<>(Kind.class);

    FailureCounters() {
        for (Kind kind : Kind.values()) {
            counts.put(kind, new AtomicLong());
        }
    }

    void increment(Kind kind) {
        counts.get(kind).incrementAndGet();
    }

    long get(Kind kind) {
        return counts.get(kind).get();
    }

    long total() {
        long sum = 0;
        for (AtomicLong count : counts.values()) {
            sum += count.get();
        }
        return sum;
    }

    /** Phân loại lỗi I/O xảy ra GIỮA LÚC CHƠI (sau bắt tay). */
    static Kind classifyInWorld(IOException failure) {
        return switch (failure) {
            case SocketTimeoutException _ -> Kind.TIMEOUT;
            case RoundTrip.ResponseMissingException _ -> Kind.RESPONSE_MISSING;
            default -> Kind.CONNECTION_LOST;
        };
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder();
        for (Kind kind : Kind.values()) {
            if (!text.isEmpty()) {
                text.append(' ');
            }
            text.append(kind.name().toLowerCase(Locale.ROOT)).append('=').append(get(kind));
        }
        return text.toString();
    }
}

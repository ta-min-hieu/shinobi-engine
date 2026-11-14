package dev.kage.server.gateway;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Chống spam tin riêng tới người lạ (ADR-369) — mỗi người gửi chỉ được nhắn riêng tối đa {@value #MAX_STRANGERS}
 * người lạ KHÁC NHAU trong {@value #WINDOW_MILLIS} ms; nhắn tiếp người đã có trong danh sách thì không tính thêm.
 * Chỉ trong RAM (khởi động lại server thì xoá; thoát game KHÔNG xoá — tránh đăng nhập lại để lách), thao tác
 * {@code synchronized}.
 */
final class StrangerWhisperLimiter {

    /** Số người lạ khác nhau tối đa trong một cửa sổ. */
    static final int MAX_STRANGERS = 5;

    /** Cửa sổ đếm. */
    static final long WINDOW_MILLIS = 10L * 60L * 1000L;

    private final Map<Long, LinkedHashMap<Long, Long>> bySender = new HashMap<>();

    /** {@code true} nếu {@code senderId} được nhắn {@code strangerId} lúc này (và ghi nhận lần nhắn). */
    synchronized boolean allow(long senderId, long strangerId, long nowMillis) {
        LinkedHashMap<Long, Long> recent = bySender.computeIfAbsent(senderId, id -> new LinkedHashMap<>());
        Iterator<Map.Entry<Long, Long>> expired = recent.entrySet().iterator();
        while (expired.hasNext()) {
            if (nowMillis - expired.next().getValue() >= WINDOW_MILLIS) {
                expired.remove();
            }
        }
        if (!recent.containsKey(strangerId) && recent.size() >= MAX_STRANGERS) {
            return false;
        }
        recent.put(strangerId, nowMillis);
        return true;
    }
}

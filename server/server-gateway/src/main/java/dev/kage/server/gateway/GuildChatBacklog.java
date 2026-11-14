package dev.kage.server.gateway;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tin bang lúc vắng (ADR-304) — giữ {@value #CAPACITY} tin chat bang gần nhất của MỖI bang trong RAM. Người vào game
 * nhận lại những tin gửi sau lần cuối họ rời world. Không lưu DB: server khởi động lại thì mất (chủ dự án chọn món nhẹ;
 * lưu bền là việc riêng).
 */
final class GuildChatBacklog {

    /** Số tin giữ mỗi bang. */
    static final int CAPACITY = 50;

    /** Một tin bang. */
    record Line(String sender, String body, Instant at) {}

    private final Map<Long, Deque<Line>> byGuild = new ConcurrentHashMap<>();

    /** Ghi một tin; quá {@value #CAPACITY} thì bỏ tin cũ nhất. */
    void record(long guildId, Line line) {
        Deque<Line> lines = byGuild.computeIfAbsent(guildId, id -> new ArrayDeque<>());
        synchronized (lines) {
            lines.addLast(line);
            while (lines.size() > CAPACITY) {
                lines.removeFirst();
            }
        }
    }

    /** ADR-401 — tối đa {@code limit} tin gần nhất của bang, cũ trước. */
    List<Line> recent(long guildId, int limit) {
        Deque<Line> lines = byGuild.get(guildId);
        if (lines == null) {
            return List.of();
        }
        synchronized (lines) {
            List<Line> all = new ArrayList<>(lines);
            return List.copyOf(all.subList(Math.max(0, all.size() - limit), all.size()));
        }
    }

    /** Các tin của bang gửi SAU {@code after}, cũ trước. */
    List<Line> since(long guildId, Instant after) {
        Deque<Line> lines = byGuild.get(guildId);
        if (lines == null) {
            return List.of();
        }
        List<Line> result = new ArrayList<>();
        synchronized (lines) {
            for (Line line : lines) {
                if (line.at().isAfter(after)) {
                    result.add(line);
                }
            }
        }
        return result;
    }
}

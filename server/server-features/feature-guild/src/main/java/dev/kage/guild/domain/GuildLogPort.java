package dev.kage.guild.domain;

import java.time.Instant;
import java.util.List;

/** Nhật ký bang (ADR-180) — đọc các dòng gần nhất của bang mà người hỏi đang ở. Chạm DB. */
public interface GuildLogPort {

    /**
     * Một dòng. {@code kind} là tên loại ({@code JOINED}, {@code DONATED}…); tên người là tên HIỆN TẠI ("" nếu không có
     * / nhân vật không còn).
     */
    record Entry(String kind, String actorName, String subjectName, long amount, String detail, Instant at) {}

    /** Mới nhất trước, tối đa {@code limit} dòng; rỗng nếu người hỏi không ở bang nào. */
    List<Entry> recent(long characterId, int limit);
}

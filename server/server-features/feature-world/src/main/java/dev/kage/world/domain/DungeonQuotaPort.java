package dev.kage.world.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Lượt Bí Cảnh mỗi ngày + ghi lần đầu hạ boss (ADR-099) — hiện thực ở {@code server-database}. */
public interface DungeonQuotaPort {

    /**
     * Trừ một lượt cho MỌI người trong {@code characterIds} trong một giao dịch (khoá dòng theo thứ tự id), hoặc không
     * trừ ai nếu có người đã dùng hết {@code maxEntries}.
     *
     * @return người đầu tiên đã hết lượt; rỗng nếu đã trừ cho cả nhóm
     */
    Optional<Long> tryConsume(List<Long> characterIds, String dungeonCode, LocalDate day, int maxEntries);

    /** Lượt đã dùng hôm nay. */
    int usedOn(long characterId, String dungeonCode, LocalDate day);

    /** Ghi lần đầu hạ boss; {@code true} nếu lần này là lần đầu. */
    boolean recordFirstClear(long characterId, String dungeonCode);
}

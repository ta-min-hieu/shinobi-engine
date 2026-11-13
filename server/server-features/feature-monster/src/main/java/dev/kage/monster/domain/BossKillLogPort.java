package dev.kage.monster.domain;

import java.time.Instant;
import java.util.List;

/** Lịch sử boss đã hạ (ADR-296) — mỗi người tham gia một dòng audit {@code BOSS_KILL}. */
public interface BossKillLogPort {

    /** Boss thế giới hay boss Bí Cảnh. */
    enum Kind {
        WORLD,
        DUNGEON
    }

    /** Một lần tham gia hạ boss. {@code rank}/{@code damage} = 0 nếu không xếp hạng (Bí Cảnh). */
    record Kill(long characterId, String bossCode, Kind kind, int rank, long damage) {}

    /** Một dòng khi đọc lại. */
    record Entry(String bossCode, Kind kind, int rank, long damage, Instant at) {}

    /** ADR-303 — một dòng bảng xếp hạng: tổng sát thương boss thế giới mọi thời của một người. */
    record Ranked(String name, long totalDamage, int kills) {}

    /** ADR-303 — top {@code limit} người theo tổng sát thương boss thế giới, cao trước. */
    List<Ranked> topWorldDamage(int limit);

    /** Ghi một lượt (mọi người tham gia của một trận) trong một giao dịch. */
    void record(List<Kill> kills);

    /** Tối đa {@code limit} dòng gần nhất của {@code characterId}, mới trước. */
    List<Entry> recent(long characterId, int limit);
}

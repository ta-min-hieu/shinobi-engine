package dev.kage.chat.domain;

/**
 * Kênh chat (GAMEPLAY.md §10). {@code GUILD} thêm ở Phase 7 lát cắt 1 cùng
 * {@code feature-guild} thật (2026-07-23); {@code PARTY} thêm ở lát cắt 2 cùng
 * {@code feature-party} thật (2026-07-24), xem ROADMAP.md Phase 7.
 */
public enum ChatChannel {
    WORLD,
    PRIVATE,
    AREA,
    GUILD,
    PARTY,
    /** ADR-110 (2026-10-25) — huynh đệ kết nghĩa. */
    SWORN,
    /** ADR-152 (2026-11-02) — loa thế giới bằng Truyền Âm Phù; không đi lệnh chat thường. */
    BROADCAST,
    /** ADR-222 (2026-11-08) — kênh giao dịch: phát toàn server như {@link #WORLD}. */
    TRADE
}

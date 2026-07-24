package dev.kage.chat.domain;

/**
 * Kênh chat (GAMEPLAY.md §10). {@code GUILD} thêm ở Phase 7 lát cắt 1 cùng lúc {@code
 * feature-guild} thật (quyết định 2026-07-23, xem ROADMAP.md Phase 6/7) — Party vẫn CHƯA có.
 */
public enum ChatChannel {
    WORLD,
    PRIVATE,
    AREA,
    GUILD
}

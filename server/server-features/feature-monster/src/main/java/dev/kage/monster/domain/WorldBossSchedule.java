package dev.kage.monster.domain;

/**
 * Lịch một boss thế giới (ADR-081 D1) — bảng {@code world_boss_schedule}.
 *
 * @param monsterCode mã boss trong {@code monster_template} (phải {@code is_boss})
 * @param spawnX toạ độ x ở world chính
 * @param spawnY toạ độ y ở world chính
 * @param intervalMinutes phút chờ giữa lúc con trước chết (hoặc lúc khởi động) và lần xuất hiện kế
 */
public record WorldBossSchedule(String monsterCode, float spawnX, float spawnY, int intervalMinutes) {}

package dev.kage.server.world;

/**
 * Một chiêu của một linh căn, nhìn từ ngoài server-world (ADR-071) — để gateway kiểm "chiêu này có thuộc linh căn
 * hiện tại không" mà không lộ {@code SkillRegistry}.
 *
 * @param code mã chiêu
 * @param slotIndex ô 0..3
 * @param requiredLevel cấp mở chiêu
 */
public record FactionSkillSlot(String code, int slotIndex, int requiredLevel) {}

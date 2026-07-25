package dev.kage.server.world;

/**
 * Loại hiệu ứng 1 lần dùng skill THẬT tạo ra — script trả field {@code effect} (string), Java parse
 * thành enum này rồi rẽ nhánh áp dụng (Phase 8, 4 hệ phái — xem {@link SkillSystem}). Chiêu boss
 * ({@link BossAiSystem}) luôn {@link #DAMAGE} (chưa có heal/shield/buff cho boss ở lát cắt này).
 *
 * <p>{@code public} vì {@code WorldOutboundSink.SkillOutcome} (dùng bởi server-gateway) cần tham
 * chiếu — gateway map sang {@code dev.kage.protocol.EffectKind} (protobuf) lúc phát sự kiện.
 */
public enum SkillEffectKind {
    DAMAGE,
    HEAL,
    SHIELD,
    BUFF,
    DEBUFF
}

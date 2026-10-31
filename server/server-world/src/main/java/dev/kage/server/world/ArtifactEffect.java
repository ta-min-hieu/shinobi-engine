package dev.kage.server.world;

/**
 * Hiệu ứng chiêu chủ động của pháp bảo (ADR-127) — mỗi loại tái dùng một cơ chế World đã có: khiên
 * ({@code StatusEffectType.SHIELD}), hồi máu (đường hồi của đan dược), tăng tốc ({@code MOVE_SPEED_MOD}).
 */
public enum ArtifactEffect {
    /** Khiên hấp thụ {@code magnitudePercent}% máu tối đa trong {@code durationSeconds}. */
    SHIELD,
    /** Hồi ngay {@code magnitudePercent}% máu tối đa. */
    HEAL,
    /** Tăng {@code magnitudePercent}% tốc chạy trong {@code durationSeconds}. */
    SPEED
}

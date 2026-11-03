package dev.kage.character.domain;

import java.time.Instant;
import java.util.Optional;

/** Huyễn hình trang phục (ADR-166) — bậc trang phục người chơi chọn hiển thị (1–3); rỗng = theo cảnh giới. */
public interface CostumePort {

    /** Kết quả chọn. */
    enum Outcome {
        OK,
        NOT_ENOUGH_GOLD
    }

    /** Bậc đã chọn; rỗng nếu chưa chọn (tự theo cảnh giới). */
    Optional<Integer> chosenTier(long characterId);

    /**
     * Chọn bậc {@code tier} (1–3) với phí {@code fee} vàng, hoặc {@code tier == 0} để trở về theo cảnh giới (miễn
     * phí) — một transaction, audit.
     */
    Outcome choose(long characterId, int tier, long fee, Instant at);
}

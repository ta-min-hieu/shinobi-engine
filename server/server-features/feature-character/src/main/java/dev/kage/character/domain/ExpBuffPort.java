package dev.kage.character.domain;

import java.time.Instant;
import java.util.Optional;

/**
 * Đan x2 kinh nghiệm (ADR-164) — chỉ ĐỌC hạn x2 EXP; phần ghi nằm trong giao dịch dùng vật phẩm (feature-inventory).
 */
public interface ExpBuffPort {

    /** Hạn x2 EXP đã ghi (có thể đã qua); rỗng nếu chưa từng dùng. */
    Optional<Instant> expiresAt(long characterId);
}

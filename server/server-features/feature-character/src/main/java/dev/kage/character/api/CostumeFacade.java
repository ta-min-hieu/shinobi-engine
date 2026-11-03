package dev.kage.character.api;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import dev.kage.character.domain.CostumePort;

/**
 * Huyễn hình trang phục (ADR-166). Bậc đã chọn được đọc MỖI lần ghi ngoại hình (vào world, lên cấp, đổi hệ) nên nhớ
 * trong RAM — một lần đọc DB mỗi nhân vật; {@link #choose} cập nhật bộ nhớ khi thành công. Chạm DB — chỉ gọi trên
 * Virtual Thread.
 */
public final class CostumeFacade {

    /** Phí mỗi lần đổi sang một bậc cụ thể; trở về "theo cảnh giới" miễn phí. PLACEHOLDER (Điều 27). */
    public static final long CHANGE_FEE = 2_000L;

    /** Composition không có huyễn hình (test): không ai chọn gì. */
    public static final CostumeFacade DISABLED = new CostumeFacade(new CostumePort() {
        @Override
        public Optional<Integer> chosenTier(long characterId) {
            return Optional.empty();
        }

        @Override
        public Outcome choose(long characterId, int tier, long fee, Instant at) {
            return Outcome.NOT_ENOUGH_GOLD;
        }
    });

    private static final int AUTO = 0;

    private final CostumePort port;
    private final Map<Long, Integer> chosen = new ConcurrentHashMap<>();

    /** Dựng ở composition root. */
    public CostumeFacade(CostumePort port) {
        this.port = port;
    }

    /** Bậc đã chọn (1–3), hoặc 0 = theo cảnh giới. */
    public int chosenTier(long characterId) {
        return chosen.computeIfAbsent(characterId, id -> port.chosenTier(id).orElse(AUTO));
    }

    /** Chọn bậc (1–3) hoặc 0 = theo cảnh giới. Người gọi đã kiểm bậc đó đã mở. */
    public CostumePort.Outcome choose(long characterId, int tier, Instant at) {
        CostumePort.Outcome outcome = port.choose(characterId, tier, tier == AUTO ? 0L : CHANGE_FEE, at);
        if (outcome == CostumePort.Outcome.OK) {
            chosen.put(characterId, tier);
        }
        return outcome;
    }
}

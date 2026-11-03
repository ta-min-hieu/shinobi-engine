package dev.kage.server.gateway;

import java.util.function.LongToIntFunction;

import dev.kage.protocol.CostumeTier;

/**
 * Huyễn hình trang phục (ADR-166) — áp bậc người chơi chọn lên ngoại hình MỖI lần registry ghi ngoại hình (vào world,
 * lên cấp, đổi hệ, trùng sinh), nên không nơi nào dựng ngoại hình phải tự nhớ. Bậc hiện = min(bậc chọn, bậc đã mở theo
 * cảnh giới): không bao giờ lộ bậc chưa mở.
 */
final class CostumeChoiceHooks {

    private volatile LongToIntFunction chosenTier = characterId -> 0;

    /** Gắn nguồn bậc đã chọn (0 = theo cảnh giới) — composition root. */
    void bind(LongToIntFunction source) {
        this.chosenTier = source;
    }

    CharacterAppearance apply(long characterId, CharacterAppearance appearance) {
        int unlocked = appearance.costumeTier().getNumber();
        if (unlocked <= 0) {
            return appearance; // chưa biết ngoại hình (test, đã ngắt kết nối)
        }
        int chosen = chosenTier.applyAsInt(characterId);
        if (chosen <= 0 || chosen >= unlocked) {
            return appearance;
        }
        return new CharacterAppearance(appearance.faction(), CostumeTier.forNumber(chosen), appearance.level());
    }
}

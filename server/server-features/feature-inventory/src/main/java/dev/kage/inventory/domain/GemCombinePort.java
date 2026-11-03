package dev.kage.inventory.domain;

import java.util.List;

/** Ghép Linh Ngọc (ADR-161) — N viên cùng loại cùng bậc + vàng ⇒ 1 viên bậc trên, 100% thành công. */
public interface GemCombinePort {

    /**
     * Một công thức nhìn từ một nhân vật.
     *
     * @param owned số viên NGUỒN người đó có thể đem ghép (chưa khoá, không đang chào giao dịch)
     */
    record Recipe(long sourceItemTemplateId, long resultItemTemplateId, int sourceQuantity, long goldFee, int owned) {}

    /** Kết quả một lần ghép. */
    enum Outcome {
        OK,
        /** Viên này không có công thức (bậc cao nhất, hoặc không phải ngọc). */
        UNKNOWN_RECIPE,
        NOT_ENOUGH_GEMS,
        NOT_ENOUGH_GOLD,
        BAG_FULL
    }

    /** Mọi công thức, theo thứ tự hiển thị. */
    List<Recipe> recipes(long characterId);

    /** Ghép MỘT lần theo công thức của viên nguồn — một transaction, khoá túi trước. */
    Outcome combine(long characterId, long sourceItemTemplateId);
}

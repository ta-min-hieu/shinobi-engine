package dev.kage.achievement.domain;

import java.util.Map;

/** Chỉ số cộng của từng danh hiệu (ADR-210) — bảng tham số, đọc một lần lúc khởi động. */
public interface TitleBonusPort {

    /** Chỉ số cộng phẳng khi đeo một danh hiệu. */
    record TitleBonus(int atk, int def, int res, int hp) {

        /** Không cộng gì. */
        public static final TitleBonus NONE = new TitleBonus(0, 0, 0, 0);
    }

    /** {@code mã thành tựu → chỉ số cộng}, chỉ những danh hiệu cộng ít nhất một chỉ số. */
    Map<String, TitleBonus> bonusesByCode();
}

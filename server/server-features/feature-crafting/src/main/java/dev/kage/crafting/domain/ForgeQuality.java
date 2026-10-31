package dev.kage.crafting.domain;

import java.util.random.RandomGenerator;

/**
 * Phẩm chất một món luyện khí (ADR-120 D3) — server tung lúc luyện, quyết số dòng tẩy luyện (ADR-111) gieo sẵn trên
 * món: thường 70% (0 dòng), tốt 25% (1 dòng), hiếm 5% (2 dòng). Hàm thuần; số ngẫu nhiên đưa vào từ ngoài.
 */
public enum ForgeQuality {
    COMMON(0),
    FINE(1),
    RARE(2);

    /** Ngưỡng trên 100: [0, 70) thường, [70, 95) tốt, [95, 100) hiếm. */
    static final int FINE_FROM = 70;

    static final int RARE_FROM = 95;

    private static final int PERCENT = 100;

    private final int refinementLines;

    ForgeQuality(int refinementLines) {
        this.refinementLines = refinementLines;
    }

    /** Số dòng tẩy luyện gieo sẵn trên món. */
    public int refinementLines() {
        return refinementLines;
    }

    /** Tung phẩm chất. */
    public static ForgeQuality roll(RandomGenerator random) {
        return fromPercentile(random.nextInt(PERCENT));
    }

    static ForgeQuality fromPercentile(int percentile) {
        if (percentile >= RARE_FROM) {
            return RARE;
        }
        return percentile >= FINE_FROM ? FINE : COMMON;
    }
}

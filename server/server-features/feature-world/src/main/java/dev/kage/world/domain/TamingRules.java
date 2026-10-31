package dev.kage.world.domain;

/**
 * Luật thu phục yêu thú (ADR-124) — hàm thuần. Quái phải còn ≤ {@value #MAX_HP_PERCENT}% máu, người dùng phù đứng trong
 * {@value #RANGE_METERS} m; tỉ lệ = tỉ lệ gốc của loài, trừ {@value #LEVEL_GAP_PENALTY}% cho mỗi cấp quái hơn người,
 * không dưới {@value #MIN_CHANCE_PERCENT}%.
 */
public final class TamingRules {

    public static final int MAX_HP_PERCENT = 20;
    public static final float RANGE_METERS = 6f;
    public static final int LEVEL_GAP_PENALTY = 5;
    public static final int MIN_CHANCE_PERCENT = 5;

    /** Mã vật phẩm Phong Yêu Phù — tiêu một lá mỗi lần thử. */
    public static final String TALISMAN_CODE = "seal_demon_talisman";

    private TamingRules() {}

    /** Tỉ lệ thu phục (%) theo tỉ lệ gốc của loài và chênh cấp. */
    public static int chancePercent(int baseChancePercent, int monsterLevel, int characterLevel) {
        int penalty = Math.max(0, monsterLevel - characterLevel) * LEVEL_GAP_PENALTY;
        return Math.max(Math.min(MIN_CHANCE_PERCENT, baseChancePercent), baseChancePercent - penalty);
    }
}

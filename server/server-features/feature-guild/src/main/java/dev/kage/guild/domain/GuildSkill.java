package dev.kage.guild.domain;

/**
 * Kỹ năng bang (ADR-085) — buff thụ động cho MỌI thành viên, bang chủ nâng bằng điểm kỹ năng bang. Mọi con số là
 * PLACEHOLDER cân bằng.
 */
public enum GuildSkill {
    /** +5% kinh nghiệm đánh quái mỗi bậc. */
    INSIGHT(5),
    /** +15 máu tối đa mỗi bậc. */
    VITALITY(15),
    /** +3 thủ mỗi bậc. */
    BULWARK(3);

    /** Bậc tối đa của mỗi kỹ năng. */
    public static final int MAX_RANK = 4;

    /** Điểm kỹ năng bang mỗi cấp bang (cấp 1 = 2 điểm, cấp 5 = 10). */
    public static final int POINTS_PER_GUILD_LEVEL = 2;

    private final int perRank;

    GuildSkill(int perRank) {
        this.perRank = perRank;
    }

    /** Hiệu lực mỗi bậc (phần trăm với INSIGHT, điểm chỉ số với hai kỹ năng kia). */
    public int perRank() {
        return perRank;
    }
}

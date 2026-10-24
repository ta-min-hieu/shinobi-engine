package dev.kage.quest.domain;

/**
 * Một mẫu truy nã (ADR-101) — tham số đọc từ {@code bounty_template}.
 *
 * @param monsterCode loài quái phải hạ
 * @param killCount số con phải hạ
 * @param minLevel cấp tối thiểu để nhận
 */
public record BountyTemplate(
        String code, String monsterCode, int killCount, long rewardGold, long rewardExp, int minLevel) {

    public BountyTemplate {
        if (killCount <= 0 || rewardGold < 0 || rewardExp < 0) {
            throw new IllegalArgumentException("bounty_template " + code + ": số âm hoặc 0");
        }
    }
}

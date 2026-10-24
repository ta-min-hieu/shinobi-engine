package dev.kage.server.gateway;

import java.util.Optional;

import dev.kage.guild.api.GuildSkillFacade;
import dev.kage.guild.domain.GuildSkill;
import dev.kage.guild.domain.GuildSkillPort;
import dev.kage.server.world.EquipmentBonusSnapshot;

/**
 * Hiệu lực kỹ năng bang (ADR-085) quy ra thứ server-world hiểu: chỉ số cộng phẳng (máu tối đa, thủ — đi chung ống chỉ
 * số trang bị qua {@link SkillRankSource}) và hệ số kinh nghiệm đánh quái. Chạm DB — chỉ gọi trên Virtual Thread.
 */
final class GuildSkillEffects {

    private static final long PERCENT = 100L;

    private GuildSkillEffects() {}

    /** Chỉ số cộng thêm từ kỹ năng bang của bang người này (rỗng nếu không ở bang nào). */
    static EquipmentBonusSnapshot bonusOf(GuildSkillFacade skills, long characterId) {
        Optional<GuildSkillPort.GuildSkills> guild = skills.skillsOf(characterId);
        if (guild.isEmpty()) {
            return EquipmentBonusSnapshot.NONE;
        }
        int hp = guild.get().rankOf(GuildSkill.VITALITY) * GuildSkill.VITALITY.perRank();
        int def = guild.get().rankOf(GuildSkill.BULWARK) * GuildSkill.BULWARK.perRank();
        return new EquipmentBonusSnapshot(0, def, 0, hp);
    }

    /** {@code amount} kinh nghiệm đánh quái sau Ngộ Tính của bang (+5% mỗi bậc, làm tròn xuống). */
    static long boostedExp(GuildSkillFacade skills, long characterId, long amount) {
        int percent = skills.skillsOf(characterId)
                .map(guild -> guild.rankOf(GuildSkill.INSIGHT) * GuildSkill.INSIGHT.perRank())
                .orElse(0);
        return percent == 0 ? amount : amount * (PERCENT + percent) / PERCENT;
    }
}

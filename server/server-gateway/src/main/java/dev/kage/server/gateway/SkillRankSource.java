package dev.kage.server.gateway;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.kage.character.api.SkillRankFacade;
import dev.kage.character.application.SkillRankUseCase;
import dev.kage.server.world.Element;
import dev.kage.server.world.FactionSkillSlot;
import dev.kage.server.world.SkillRankSnapshot;
import dev.kage.server.world.WorldFacade;

/**
 * Cầu nối feature-character ↔ server-world cho bậc chiêu (ADR-071): dịch bậc theo MÃ chiêu sang bậc theo Ô của
 * linh căn hiện tại, và đưa danh sách chiêu của linh căn cho use case kiểm lệnh nâng. Chạm DB: Virtual Thread.
 */
final class SkillRankSource {

    // volatile, không final: registry dựng TRƯỚC DB — bind() sau, cùng lý do AchievementHooks.
    private volatile SkillRankFacade facade = SkillRankFacade.inMemory();

    /** Nối bản thật — một lần, lúc khởi động. */
    void bind(SkillRankFacade realFacade) {
        this.facade = realFacade;
    }

    SkillRankFacade facade() {
        return facade;
    }

    /** Bậc theo ô của các chiêu thuộc {@code element}. */
    SkillRankSnapshot snapshotFor(long characterId, Element element) {
        Map<String, Integer> ranks = facade.ranksOf(characterId);
        int[] bySlot = {1, 1, 1, 1};
        for (FactionSkillSlot skill : WorldFacade.skillsOf(element)) {
            if (skill.slotIndex() >= 0 && skill.slotIndex() < bySlot.length) {
                bySlot[skill.slotIndex()] = ranks.getOrDefault(skill.code(), 1);
            }
        }
        return new SkillRankSnapshot(bySlot[0], bySlot[1], bySlot[2], bySlot[3]);
    }

    /** Chiêu của {@code element} theo thứ tự ô, dạng use case cần. */
    static List<SkillRankUseCase.FactionSkill> factionSkills(Element element) {
        List<SkillRankUseCase.FactionSkill> skills = new ArrayList<>();
        for (FactionSkillSlot skill : WorldFacade.skillsOf(element)) {
            skills.add(new SkillRankUseCase.FactionSkill(skill.code(), skill.requiredLevel()));
        }
        return skills;
    }
}

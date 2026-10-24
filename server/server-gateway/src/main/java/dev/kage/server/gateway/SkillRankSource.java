package dev.kage.server.gateway;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;

import dev.kage.character.api.SkillRankFacade;
import dev.kage.character.application.SkillRankUseCase;
import dev.kage.server.world.Element;
import dev.kage.server.world.EquipmentBonusSnapshot;
import dev.kage.server.world.FactionSkillSlot;
import dev.kage.server.world.MentalArt;
import dev.kage.server.world.MentalArts;
import dev.kage.server.world.SkillRankSnapshot;
import dev.kage.server.world.WorldFacade;

/**
 * Cầu nối feature-character ↔ server-world cho bậc chiêu (ADR-071): dịch bậc theo MÃ chiêu sang bậc theo Ô của
 * linh căn hiện tại, và đưa danh sách chiêu của linh căn cho use case kiểm lệnh nâng. Chạm DB: Virtual Thread.
 */
final class SkillRankSource {

    // volatile, không final: registry dựng TRƯỚC DB — bind() sau, cùng lý do AchievementHooks.
    private volatile SkillRankFacade facade = SkillRankFacade.inMemory();

    // Linh căn hiện tại theo DB — tâm pháp chỉ tính trên linh căn hiện tại (ADR-077). Mặc định NONE: test
    // dựng registry không DB thì không có tâm pháp nào.
    private volatile LongFunction<Element> currentElement = characterId -> Element.NONE;

    // ADR-085 — chỉ số cộng thêm từ kỹ năng bang; mặc định không có bang.
    private volatile LongFunction<EquipmentBonusSnapshot> guildBonus = characterId -> EquipmentBonusSnapshot.NONE;

    /** Nối bản thật — một lần, lúc khởi động. */
    void bind(SkillRankFacade realFacade, LongFunction<Element> elementLookup) {
        this.facade = realFacade;
        this.currentElement = elementLookup;
    }

    /**
     * {@code equipment} cộng tâm pháp bị động đã học của linh căn hiện tại (ADR-077) và kỹ năng bang (ADR-085) — đường
     * DUY NHẤT gộp hai
     * thứ, nên mọi chỗ áp chỉ số (vào world, mặc/gỡ đồ, linh thú lên cấp, đổi linh căn, bảng nhân vật) thấy
     * cùng một con số. Chạm DB: Virtual Thread.
     */
    EquipmentBonusSnapshot withMentalArts(long characterId, EquipmentBonusSnapshot equipment) {
        EquipmentBonusSnapshot arts =
                MentalArts.applyTo(equipment, currentElement.apply(characterId), facade.ranksOf(characterId));
        EquipmentBonusSnapshot guild = guildBonus.apply(characterId);
        return arts.withStats(
                arts.atk() + guild.atk(), arts.def() + guild.def(), arts.res() + guild.res(), arts.hp() + guild.hp());
    }

    /** Nối kỹ năng bang (ADR-085) — một lần, lúc khởi động. */
    void bindGuildBonus(LongFunction<EquipmentBonusSnapshot> lookup) {
        this.guildBonus = lookup;
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

    /** Chiêu của {@code element} theo thứ tự ô, rồi tới tâm pháp (ADR-077), dạng use case cần. */
    static List<SkillRankUseCase.FactionSkill> factionSkills(Element element) {
        List<SkillRankUseCase.FactionSkill> skills = new ArrayList<>();
        for (FactionSkillSlot skill : WorldFacade.skillsOf(element)) {
            skills.add(new SkillRankUseCase.FactionSkill(skill.code(), skill.requiredLevel()));
        }
        for (MentalArt art : MentalArts.of(element)) {
            skills.add(new SkillRankUseCase.FactionSkill(art.code(), art.requiredLevel(), true));
        }
        return skills;
    }
}

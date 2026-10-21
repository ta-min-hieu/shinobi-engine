package dev.kage.server.gateway;

import java.util.Optional;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.CharacterStats;
import dev.kage.character.domain.PositionSnapshot;
import dev.kage.inventory.domain.EquipmentBonus;
import dev.kage.server.world.CharacterBaseStats;
import dev.kage.server.world.CharacterEntry;
import dev.kage.server.world.Element;
import dev.kage.server.world.EquipmentBonusSnapshot;
import dev.kage.server.world.SkillRankSnapshot;
import dev.kage.server.world.WorldFacade;

/**
 * Chỉ số HIỆU LỰC của một nhân vật sắp vào một World: chỉ số nền + chỉ số trang bị — nơi DUY NHẤT
 * gateway ghép hai thứ đó lại cho MỌI đường attach (world chính, hầm ngục, đấu trường, và đường về
 * world chính sau hầm ngục/trận đấu).
 *
 * <p><b>Vì sao có lớp này (sửa 2026-09-29).</b> Trước đây mỗi registry tự đọc chỉ số nền bằng một
 * bản sao {@code loadBaseStats} riêng, còn bonus trang bị thì mỗi đường tự nhớ mà đọc — và đường
 * VÀO hầm ngục/đấu trường đã quên: nhân vật mặc giáp +máu vào hầm ngục với 100 máu, và
 * {@code WorldChangedEvent} (ADR-051) cũng báo 100. Gói cả hai vào một record rồi chỉ cho attach
 * qua {@link #entryAt} là cách làm cho "quên bonus" không còn diễn đạt được — cùng lý lẽ với
 * {@link CharacterEntry}.
 *
 * <p>Máu tối đa hỏi CÔNG THỨC ({@link WorldFacade#characterMaxHp}), không hỏi entity: lệnh attach
 * chỉ mới xếp hàng. Attach luôn cho máu đầy của máu tối đa HIỆU LỰC (ADR-046/ADR-051).
 *
 * @param baseStats chỉ số nền (STR/AGI/INT/VIT, hệ, cấp)
 * @param equipmentBonus tổng chỉ số trang bị đang mặc, đã tính cường hoá
 */
record CharacterLoadout(
        CharacterBaseStats baseStats, EquipmentBonusSnapshot equipmentBonus, SkillRankSnapshot skillRanks) {

    /** Không kèm bậc chiêu — đường chọn nhân vật tự nạp bậc ngay sau (xem {@link #withSkillRanks}). */
    CharacterLoadout(CharacterBaseStats baseStats, EquipmentBonusSnapshot equipmentBonus) {
        this(baseStats, equipmentBonus, SkillRankSnapshot.NONE);
    }

    /** Bản kèm bậc chiêu (ADR-071). */
    CharacterLoadout withSkillRanks(SkillRankSnapshot ranks) {
        return new CharacterLoadout(baseStats, equipmentBonus, ranks);
    }

    /**
     * Đọc chỉ số nền và trang bị hiện tại từ DB. Chạm DB — chỉ gọi trên Virtual Thread
     * ({@code authExecutor}/{@code settlementExecutor}/{@code defeatExecutor}), không bao giờ trên
     * EventLoop hay tick thread.
     */
    static CharacterLoadout load(
            CharacterFacade characterFacade, ConnectedCharacterRegistry registry, long characterId) {
        CharacterStats stats = characterFacade.loadStats(characterId);
        Optional<Character> character = characterFacade.findById(characterId);
        Element element =
                character.map(c -> FactionMapping.toElement(c.faction())).orElse(Element.NONE);
        int level = character.map(Character::level).orElse(1);
        CharacterBaseStats baseStats =
                new CharacterBaseStats(stats.str(), stats.agi(), stats.intelligence(), stats.vit(), element, level);
        return new CharacterLoadout(
                baseStats,
                registry.equipmentBonusOf(characterId),
                registry.skillRanks.snapshotFor(characterId, element));
    }

    /**
     * Chỉ số nền từ nhân vật + chỉ số đã đọc sẵn — cho đường chọn nhân vật, nơi {@link Character}
     * đã có trong tay nên không đọc DB lần hai.
     */
    static CharacterBaseStats baseStatsOf(Character character, CharacterStats stats) {
        return new CharacterBaseStats(
                stats.str(),
                stats.agi(),
                stats.intelligence(),
                stats.vit(),
                FactionMapping.toElement(character.faction()),
                character.level());
    }

    /**
     * Bản dịch {@code feature-inventory} → {@code server-world} (ADR-036 D2): server-world không
     * biết feature nào tồn tại (Điều 23), nên bản dịch phải nằm ở gateway — và chỉ MỘT bản.
     */
    static EquipmentBonusSnapshot snapshotOf(EquipmentBonus bonus, int moveSpeedPercent) {
        return new EquipmentBonusSnapshot(
                bonus.bonusAtk(), bonus.bonusDef(), bonus.bonusRes(), bonus.bonusHp(), moveSpeedPercent);
    }

    /** Lệnh attach KÈM trang bị tại {@code at} — mọi đường vào World đi qua đây. */
    CharacterEntry entryAt(long characterId, PositionSnapshot at) {
        return new CharacterEntry(characterId, at.x(), at.y(), baseStats, equipmentBonus, skillRanks);
    }

    /** Máu tối đa hiệu lực — cũng là máu lúc vào World mới (attach luôn cho máu đầy). */
    int maxHp() {
        return WorldFacade.characterMaxHp(baseStats.level(), equipmentBonus);
    }
}

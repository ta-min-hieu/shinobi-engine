package dev.kage.server.gateway;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import dev.kage.character.domain.Faction;
import dev.kage.server.world.EquipmentBonusSnapshot;

/**
 * Trận pháp nhóm (ADR-128) — luật thuần: từ số người đứng cùng trận và tập linh căn có mặt ra thưởng chỉ số phẳng.
 * Mỗi linh căn tính MỘT lần dù nhiều người cùng linh căn; số linh căn khác nhau quyết định bậc trận (thưởng máu).
 */
final class FormationRules {

    /** Bán kính quanh mình (mét) để tính người đứng cùng trận ở world chính. */
    static final float RADIUS_METERS = 12f;
    /** Từ bao nhiêu người (tính cả mình) thì thành trận. */
    static final int MIN_MEMBERS = 2;

    static final int MAJOR = 8;
    static final int MINOR = 4;
    static final int LOI_ATK = 6;
    static final int LOI_DEF = 2;
    static final int THREE_TALENTS_FACTIONS = 3;
    static final int THREE_TALENTS_HP = 40;
    static final int FIVE_ELEMENTS_HP = 100;

    /** Bậc trận theo số linh căn khác nhau. */
    enum Tier {
        PLAIN,
        THREE_TALENTS,
        FIVE_ELEMENTS
    }

    /**
     * Một trận đang đứng — so sánh bằng giá trị để biết trận của một người có ĐỔI không.
     *
     * @param members số người trong trận (tính cả mình)
     * @param factions linh căn có mặt
     */
    record Formation(int members, Set<Faction> factions, Tier tier, EquipmentBonusSnapshot bonus) {

        Formation {
            factions = Set.copyOf(factions);
        }
    }

    private FormationRules() {}

    /** Trận của một người; rỗng nếu chưa đủ {@link #MIN_MEMBERS} người hoặc không biết linh căn nào. */
    static Optional<Formation> of(int members, Set<Faction> factions) {
        if (members < MIN_MEMBERS || factions.isEmpty()) {
            return Optional.empty();
        }
        Set<Faction> present = EnumSet.copyOf(factions);
        int atk = 0;
        int def = 0;
        int res = 0;
        for (Faction faction : present) {
            EquipmentBonusSnapshot each = bonusOf(faction);
            atk += each.atk();
            def += each.def();
            res += each.res();
        }
        Tier tier = tierOf(present.size());
        int hp =
                switch (tier) {
                    case FIVE_ELEMENTS -> FIVE_ELEMENTS_HP;
                    case THREE_TALENTS -> THREE_TALENTS_HP;
                    case PLAIN -> 0;
                };
        return Optional.of(new Formation(members, present, tier, new EquipmentBonusSnapshot(atk, def, res, hp)));
    }

    /** Thưởng của MỘT linh căn có mặt trong trận. */
    static EquipmentBonusSnapshot bonusOf(Faction faction) {
        return switch (faction) {
            case HOA -> new EquipmentBonusSnapshot(MAJOR, 0, 0, 0);
            case THUY -> new EquipmentBonusSnapshot(0, 0, MAJOR, 0);
            case PHONG -> new EquipmentBonusSnapshot(MINOR, 0, MINOR, 0);
            case LOI -> new EquipmentBonusSnapshot(LOI_ATK, LOI_DEF, 0, 0);
            case THO -> new EquipmentBonusSnapshot(0, MAJOR, 0, 0);
        };
    }

    static Tier tierOf(int distinctFactions) {
        if (distinctFactions >= Faction.values().length) {
            return Tier.FIVE_ELEMENTS;
        }
        return distinctFactions >= THREE_TALENTS_FACTIONS ? Tier.THREE_TALENTS : Tier.PLAIN;
    }

    /** {@code bonus} cộng thưởng của trận (nếu có). */
    static EquipmentBonusSnapshot plus(EquipmentBonusSnapshot bonus, Optional<Formation> formation) {
        if (formation.isEmpty()) {
            return bonus;
        }
        EquipmentBonusSnapshot extra = formation.get().bonus();
        return bonus.withStats(
                bonus.atk() + extra.atk(),
                bonus.def() + extra.def(),
                bonus.res() + extra.res(),
                bonus.hp() + extra.hp());
    }
}

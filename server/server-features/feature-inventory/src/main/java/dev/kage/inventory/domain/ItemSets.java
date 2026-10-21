package dev.kage.inventory.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Luật bộ pháp khí (ADR-065) — hàm thuần. */
public final class ItemSets {

    private ItemSets() {}

    /** Những bộ có ít nhất một món trong {@code wornTemplateIds}, kèm thưởng đã đạt. */
    public static List<ItemSetProgress> progress(List<ItemSet> sets, Set<Long> wornTemplateIds) {
        List<ItemSetProgress> result = new ArrayList<>();
        for (ItemSet set : sets) {
            int worn = 0;
            for (long member : set.memberTemplateIds()) {
                if (wornTemplateIds.contains(member)) {
                    worn++;
                }
            }
            if (worn > 0) {
                result.add(new ItemSetProgress(
                        set.nameKey(), worn, set.memberTemplateIds().size(), activeBonus(set, worn)));
            }
        }
        return result;
    }

    /** Tổng thưởng của mọi bộ. */
    public static EquipmentBonus totalBonus(List<ItemSetProgress> progress) {
        EquipmentBonus total = EquipmentBonus.NONE;
        for (ItemSetProgress set : progress) {
            total = plus(total, set.activeBonus());
        }
        return total;
    }

    static EquipmentBonus plus(EquipmentBonus a, EquipmentBonus b) {
        return new EquipmentBonus(
                a.bonusAtk() + b.bonusAtk(),
                a.bonusDef() + b.bonusDef(),
                a.bonusRes() + b.bonusRes(),
                a.bonusHp() + b.bonusHp());
    }

    private static EquipmentBonus activeBonus(ItemSet set, int worn) {
        EquipmentBonus bonus = EquipmentBonus.NONE;
        for (ItemSetTier tier : set.tiers()) {
            if (worn >= tier.piecesRequired()) {
                bonus = plus(bonus, tier.bonus());
            }
        }
        return bonus;
    }
}

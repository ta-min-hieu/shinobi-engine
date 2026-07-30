package dev.kage.server.world;

import java.util.Map;

/**
 * Hệ số khắc chế nguyên tố (GAME-DESIGN-DOCUMENT.md §4/§6, bước cuối pipeline damage) — placeholder
 * ±15%, CHƯA cân bằng qua playtest (cùng tinh thần {@link CombatConstants}). Chu trình khắc GDD §4:
 * Hỏa khắc Phong, Phong khắc Thổ, Thổ khắc Thủy, Thủy khắc Hỏa (đóng vòng) — {@link Element#LOI} và
 * {@link Element#NONE} trung lập tuyệt đối, không khắc/không bị khắc hệ nào.
 */
final class ElementCounterTable {

    private static final float ADVANTAGE_MULTIPLIER = 1.15f;
    private static final float DISADVANTAGE_MULTIPLIER = 0.85f;
    private static final float NEUTRAL_MULTIPLIER = 1.0f;

    // attacker -> hệ bị attacker khắc.
    private static final Map<Element, Element> BEATS = Map.of(
            Element.HOA, Element.PHONG,
            Element.PHONG, Element.THO,
            Element.THO, Element.THUY,
            Element.THUY, Element.HOA);

    private ElementCounterTable() {}

    static float multiplier(Element attacker, Element defender) {
        if (BEATS.get(attacker) == defender) {
            return ADVANTAGE_MULTIPLIER;
        }
        if (BEATS.get(defender) == attacker) {
            return DISADVANTAGE_MULTIPLIER;
        }
        return NEUTRAL_MULTIPLIER;
    }
}

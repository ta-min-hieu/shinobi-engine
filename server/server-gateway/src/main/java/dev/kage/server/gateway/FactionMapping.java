package dev.kage.server.gateway;

import dev.kage.character.domain.Faction;
import dev.kage.server.world.Element;

/**
 * {@code feature-character.Faction} → {@code server-world.Element} — server-world KHÔNG import
 * feature-character (ranh giới module), gateway là nơi DUY NHẤT biết cả 2 nên đặt mapping ở đây.
 * Dùng ở {@code GatewayChannelHandler} (lúc attach nhân vật vào world chính) VÀ
 * {@code DungeonInstanceRegistry} (lúc attach vào world dungeon) — 2 chỗ, tách thành 1 nguồn chân
 * lý duy nhất để không lệch nhau khi thêm hệ phái mới.
 */
final class FactionMapping {

    private FactionMapping() {}

    static Element toElement(Faction faction) {
        return switch (faction) {
            case HOA -> Element.HOA;
            case THUY -> Element.THUY;
            case PHONG -> Element.PHONG;
            case LOI -> Element.LOI;
            case THO -> Element.THO;
        };
    }

    /** {@code feature-character.Faction} → enum giao thức (ngoại hình gửi qua dây). */
    static dev.kage.protocol.Faction toProto(Faction faction) {
        return switch (faction) {
            case HOA -> dev.kage.protocol.Faction.FACTION_HOA;
            case THUY -> dev.kage.protocol.Faction.FACTION_THUY;
            case PHONG -> dev.kage.protocol.Faction.FACTION_PHONG;
            case LOI -> dev.kage.protocol.Faction.FACTION_LOI;
            case THO -> dev.kage.protocol.Faction.FACTION_THO;
        };
    }
}

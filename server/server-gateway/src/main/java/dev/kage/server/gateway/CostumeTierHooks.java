package dev.kage.server.gateway;

import java.util.Optional;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.Character;

/**
 * ADR-132 — lên cấp vượt mốc cảnh giới (Cấp 19 / 37) thì đổi bậc trang phục NGAY trong phiên: ghi ngoại hình mới vào
 * registry (mọi {@code CharacterSpawnedEvent} về sau mang bậc mới) và, chỉ khi bậc THẬT SỰ đổi, xếp lệnh báo đổi
 * ngoại hình vào World đang chứa nhân vật (ADR-045 — người đang nhìn thấy họ tải lại model). Chạy trên luồng cộng EXP
 * (Virtual Thread) nên đọc DB được.
 */
final class CostumeTierHooks {

    private CostumeTierHooks() {}

    static CharacterLevelSync.LevelListener listener(
            CharacterFacade characters, ConnectedCharacterRegistry registry, WorldRouteLookup routes) {
        return (characterId, level) -> {
            Optional<Character> character = characters.findById(characterId);
            if (character.isEmpty()) {
                return;
            }
            CharacterAppearance next = CharacterAppearance.of(
                    FactionMapping.toProto(character.get().faction()), level);
            Optional<CharacterAppearance> previous = registry.updateAppearance(characterId, next);
            if (previous.isPresent() && previous.get().costumeTier() != next.costumeTier()) {
                routes.find(characterId).ifPresent(world -> world.enqueueAppearanceChanged(characterId));
            }
        };
    }
}

package dev.kage.server.gateway;

import java.util.Map;
import java.util.Optional;
import java.util.function.LongUnaryOperator;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.api.InspectPrivacyFacade;
import dev.kage.character.domain.Character;
import dev.kage.inventory.api.InventoryFacade;
import dev.kage.inventory.domain.EquipmentSlotType;
import dev.kage.inventory.domain.InventorySnapshot;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.protocol.EquipmentSlot;
import dev.kage.protocol.InspectCharacterOutcome;
import dev.kage.protocol.InspectCharacterRequest;
import dev.kage.protocol.InspectCharacterResponse;
import dev.kage.protocol.InspectedEquipment;
import dev.kage.protocol.SetInspectPrivacyCommand;
import dev.kage.protocol.SetInspectPrivacyResponse;

/**
 * Soi trang bị người khác (ADR-171) — cảnh giới, Lực Chiến, món đang mặc (cường hoá, ngọc) của một nhân vật theo tên,
 * kể cả đang offline. Người đã tắt cho xem ⇒ {@code HIDDEN} (trừ khi tự soi mình). Chỉ đọc.
 */
final class InspectCommands {

    /** Nguồn dữ liệu. */
    record Deps(CharacterFacade characters, InventoryFacade inventory, InspectPrivacyFacade privacy) {}

    private final Deps deps;

    InspectCommands(Deps deps) {
        this.deps = deps;
    }

    /**
     * Virtual Thread; {@code characterId} (người hỏi) từ session. {@code combatPowerOf} — Lực Chiến của một nhân vật,
     * do gateway handler tính bằng đúng công thức bảng nhân vật (ADR-056).
     */
    InspectCharacterResponse inspect(
            long characterId, InspectCharacterRequest request, LongUnaryOperator combatPowerOf) {
        Optional<Character> target =
                deps.characters().findByName(request.getName().strip());
        if (target.isEmpty()) {
            return outcome(InspectCharacterOutcome.INSPECT_CHARACTER_OUTCOME_NOT_FOUND);
        }
        Character found = target.get();
        if (found.id() != characterId && deps.privacy().hidden(found.id())) {
            return outcome(InspectCharacterOutcome.INSPECT_CHARACTER_OUTCOME_HIDDEN).toBuilder()
                    .setName(found.name())
                    .build();
        }
        InspectCharacterResponse.Builder response = InspectCharacterResponse.newBuilder()
                .setOutcome(InspectCharacterOutcome.INSPECT_CHARACTER_OUTCOME_OK)
                .setName(found.name())
                .setLevel(found.level())
                .setFaction(FactionMapping.toProto(found.faction()))
                .setCombatPower(combatPowerOf.applyAsLong(found.id()));
        InventorySnapshot snapshot = deps.inventory().listInventory(found.id());
        Map<Long, Long> sockets = deps.inventory().socketsOf(found.id());
        for (Map.Entry<EquipmentSlotType, Long> slot :
                snapshot.equipment().slots().entrySet()) {
            snapshot.inventory()
                    .findItem(slot.getValue())
                    .ifPresent(item -> response.addEquipment(view(slot.getKey(), item, sockets)));
        }
        return response.build();
    }

    /** Virtual Thread; bật/tắt cho người khác soi mình. */
    SetInspectPrivacyResponse setPrivacy(long characterId, SetInspectPrivacyCommand command) {
        deps.privacy().setHidden(characterId, command.getHidden());
        return SetInspectPrivacyResponse.newBuilder()
                .setHidden(command.getHidden())
                .build();
    }

    private static InspectedEquipment view(EquipmentSlotType slot, ItemStack item, Map<Long, Long> sockets) {
        return InspectedEquipment.newBuilder()
                .setSlot(EquipmentSlot.valueOf("EQUIPMENT_SLOT_" + slot.name()).getNumber())
                .setItemTemplateId(item.itemTemplateId())
                .setEnhanceLevel(item.enhanceLevel())
                .setSocketGemTemplateId(sockets.getOrDefault(item.id(), 0L))
                .build();
    }

    private static InspectCharacterResponse outcome(InspectCharacterOutcome outcome) {
        return InspectCharacterResponse.newBuilder().setOutcome(outcome).build();
    }
}

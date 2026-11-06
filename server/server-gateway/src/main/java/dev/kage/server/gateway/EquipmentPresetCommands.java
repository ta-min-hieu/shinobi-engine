package dev.kage.server.gateway;

import java.util.Map;
import java.util.function.LongToIntFunction;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.Character;
import dev.kage.inventory.api.EquipmentPresetFacade;
import dev.kage.inventory.api.InventoryFacade;
import dev.kage.inventory.domain.EquipmentSlotType;
import dev.kage.inventory.domain.Inventory;
import dev.kage.inventory.domain.InventorySnapshot;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.protocol.ApplyEquipmentPresetCommand;
import dev.kage.protocol.ApplyEquipmentPresetResponse;
import dev.kage.protocol.EquipmentPresetOutcome;
import dev.kage.protocol.EquipmentPresetSlot;
import dev.kage.protocol.EquipmentPresetView;
import dev.kage.protocol.EquipmentSlot;
import dev.kage.protocol.GetEquipmentPresetsResponse;
import dev.kage.protocol.SaveEquipmentPresetCommand;
import dev.kage.protocol.SaveEquipmentPresetResponse;

/**
 * Lưu bộ trang bị (ADR-175) — chụp các món đang mặc vào một bộ, và mặc lại cả bộ bằng một lệnh. Mặc bộ gọi
 * {@link InventoryFacade#equipItem} cho TỪNG món (đúng luật cấp/ô/sở hữu của mặc đồ thường), rồi tính lại chỉ số MỘT
 * lần.
 */
final class EquipmentPresetCommands {

    /** Nguồn dữ liệu. */
    record Deps(EquipmentPresetFacade presets, InventoryFacade inventory, CharacterFacade characters) {}

    private final Deps deps;

    EquipmentPresetCommands(Deps deps) {
        this.deps = deps;
    }

    /** Virtual Thread; {@code characterId} từ session. */
    SaveEquipmentPresetResponse save(long characterId, SaveEquipmentPresetCommand command) {
        if (!EquipmentPresetFacade.isValidPreset(command.getPresetNo())) {
            return SaveEquipmentPresetResponse.newBuilder()
                    .setOutcome(EquipmentPresetOutcome.EQUIPMENT_PRESET_OUTCOME_INVALID_PRESET)
                    .build();
        }
        InventorySnapshot snapshot = deps.inventory().listInventory(characterId);
        Map<EquipmentSlotType, Long> equipped = snapshot.equipment().slots();
        if (equipped.isEmpty()) {
            return SaveEquipmentPresetResponse.newBuilder()
                    .setOutcome(EquipmentPresetOutcome.EQUIPMENT_PRESET_OUTCOME_NOTHING_EQUIPPED)
                    .build();
        }
        deps.presets().save(characterId, command.getPresetNo(), equipped);
        return SaveEquipmentPresetResponse.newBuilder()
                .setOutcome(EquipmentPresetOutcome.EQUIPMENT_PRESET_OUTCOME_OK)
                .setPreset(view(command.getPresetNo(), equipped, snapshot.inventory()))
                .build();
    }

    /**
     * Virtual Thread; {@code characterId} từ session. {@code reapplyStats} áp lại chỉ số trang bị và trả máu tối đa mới
     * — chỉ gateway handler làm được việc đó.
     */
    ApplyEquipmentPresetResponse apply(
            long characterId, ApplyEquipmentPresetCommand command, LongToIntFunction reapplyStats) {
        Map<EquipmentSlotType, Long> preset = EquipmentPresetFacade.isValidPreset(command.getPresetNo())
                ? deps.presets().presetsOf(characterId).getOrDefault(command.getPresetNo(), Map.of())
                : Map.of();
        if (preset.isEmpty()) {
            return ApplyEquipmentPresetResponse.newBuilder()
                    .setOutcome(
                            EquipmentPresetFacade.isValidPreset(command.getPresetNo())
                                    ? EquipmentPresetOutcome.EQUIPMENT_PRESET_OUTCOME_EMPTY_PRESET
                                    : EquipmentPresetOutcome.EQUIPMENT_PRESET_OUTCOME_INVALID_PRESET)
                    .build();
        }
        // ADR-036 D4 — cấp đọc từ DB ngay đây, như mặc đồ thường.
        int level =
                deps.characters().findById(characterId).map(Character::level).orElse(0);
        Map<EquipmentSlotType, Long> wearing =
                deps.inventory().listInventory(characterId).equipment().slots();
        int equipped = 0;
        int missing = 0;
        for (Map.Entry<EquipmentSlotType, Long> slot : preset.entrySet()) {
            if (slot.getValue().equals(wearing.get(slot.getKey()))) {
                continue;
            }
            if (deps.inventory().equipItem(characterId, slot.getValue(), slot.getKey(), level)
                    == InventoryFacade.EquipResult.OK) {
                equipped++;
            } else {
                missing++;
            }
        }
        return ApplyEquipmentPresetResponse.newBuilder()
                .setOutcome(EquipmentPresetOutcome.EQUIPMENT_PRESET_OUTCOME_OK)
                .setEquipped(equipped)
                .setMissing(missing)
                .setMaxHp(equipped > 0 ? reapplyStats.applyAsInt(characterId) : 0)
                .build();
    }

    /** Virtual Thread; mọi bộ đã lưu, kèm loại món (0 nếu món không còn). */
    GetEquipmentPresetsResponse list(long characterId) {
        Inventory inventory = deps.inventory().listInventory(characterId).inventory();
        GetEquipmentPresetsResponse.Builder response = GetEquipmentPresetsResponse.newBuilder();
        deps.presets()
                .presetsOf(characterId)
                .forEach((presetNo, slots) -> response.addPresets(view(presetNo, slots, inventory)));
        return response.build();
    }

    private static EquipmentPresetView view(int presetNo, Map<EquipmentSlotType, Long> slots, Inventory inventory) {
        EquipmentPresetView.Builder view = EquipmentPresetView.newBuilder().setPresetNo(presetNo);
        slots.forEach((slot, itemId) -> view.addSlots(EquipmentPresetSlot.newBuilder()
                .setSlot(EquipmentSlot.valueOf("EQUIPMENT_SLOT_" + slot.name()))
                .setItemId(itemId)
                .setItemTemplateId(inventory
                        .findItem(itemId)
                        .map(ItemStack::itemTemplateId)
                        .orElse(0L))));
        return view.build();
    }
}

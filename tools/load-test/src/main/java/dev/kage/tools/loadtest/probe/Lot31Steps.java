package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.ApplyEquipmentPresetCommand;
import dev.kage.protocol.ApplyEquipmentPresetResponse;
import dev.kage.protocol.EquipmentPresetSlot;
import dev.kage.protocol.EquipmentPresetView;
import dev.kage.protocol.GetEquipmentPresetsRequest;
import dev.kage.protocol.GetEquipmentPresetsResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SaveEquipmentPresetCommand;
import dev.kage.protocol.SaveEquipmentPresetResponse;

/**
 * Bước lô 31: bộ trang bị (ADR-175) {@code savePreset:<n>}, {@code applyPreset:<n>}, {@code listPresets}. Nối sau
 * {@link Lot30SocialSteps}.
 */
final class Lot31Steps {

    private final Lot23Scenario.Asker asker;

    Lot31Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "savePreset" -> savePreset(Integer.parseInt(arg));
            case "applyPreset" -> applyPreset(Integer.parseInt(arg));
            case "listPresets" -> listPresets();
            default -> "bước không hiểu";
        };
    }

    private String savePreset(int presetNo) throws IOException {
        return asker.ask(
                Opcode.OPCODE_SAVE_EQUIPMENT_PRESET_COMMAND_VALUE,
                SaveEquipmentPresetCommand.newBuilder().setPresetNo(presetNo).build(),
                Opcode.OPCODE_SAVE_EQUIPMENT_PRESET_RESPONSE_VALUE,
                p -> {
                    SaveEquipmentPresetResponse r = SaveEquipmentPresetResponse.parseFrom(p);
                    return r.getOutcome().name() + " " + describe(r.getPreset());
                });
    }

    private String applyPreset(int presetNo) throws IOException {
        return asker.ask(
                Opcode.OPCODE_APPLY_EQUIPMENT_PRESET_COMMAND_VALUE,
                ApplyEquipmentPresetCommand.newBuilder().setPresetNo(presetNo).build(),
                Opcode.OPCODE_APPLY_EQUIPMENT_PRESET_RESPONSE_VALUE,
                p -> {
                    ApplyEquipmentPresetResponse r = ApplyEquipmentPresetResponse.parseFrom(p);
                    return r.getOutcome().name() + " mặc=" + r.getEquipped() + " thiếu=" + r.getMissing() + " máu="
                            + r.getMaxHp();
                });
    }

    private String listPresets() throws IOException {
        return asker.ask(
                Opcode.OPCODE_GET_EQUIPMENT_PRESETS_REQUEST_VALUE,
                GetEquipmentPresetsRequest.getDefaultInstance(),
                Opcode.OPCODE_GET_EQUIPMENT_PRESETS_RESPONSE_VALUE,
                p -> {
                    StringBuilder out = new StringBuilder();
                    for (EquipmentPresetView preset :
                            GetEquipmentPresetsResponse.parseFrom(p).getPresetsList()) {
                        out.append(describe(preset)).append(" | ");
                    }
                    return out.toString();
                });
    }

    private static String describe(EquipmentPresetView preset) {
        StringBuilder out = new StringBuilder("bộ").append(preset.getPresetNo()).append('[');
        for (EquipmentPresetSlot slot : preset.getSlotsList()) {
            out.append(slot.getSlot().name().replace("EQUIPMENT_SLOT_", ""))
                    .append('=')
                    .append(slot.getItemId())
                    .append('/')
                    .append(slot.getItemTemplateId())
                    .append(' ');
        }
        return out.append(']').toString();
    }
}

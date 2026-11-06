package dev.kage.client.core.world;

import java.util.Locale;
import java.util.function.IntConsumer;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ApplyEquipmentPresetCommand;
import dev.kage.protocol.ApplyEquipmentPresetResponse;
import dev.kage.protocol.EquipmentPresetOutcome;
import dev.kage.protocol.EquipmentPresetSlot;
import dev.kage.protocol.EquipmentPresetView;
import dev.kage.protocol.GetEquipmentPresetsRequest;
import dev.kage.protocol.GetEquipmentPresetsResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SaveEquipmentPresetCommand;
import dev.kage.protocol.SaveEquipmentPresetResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Lưu bộ trang bị (ADR-175) — lệnh chat {@code /bodo} (hoặc {@code /preset}): không đối số ⇒ liệt kê các bộ;
 * {@code /bodo luu <1-3>} lưu đồ đang mặc; {@code /bodo <1-3>} (hoặc {@code /bodo mac <1-3>}) mặc cả bộ. Server mặc
 * từng món qua đường mặc đồ thường; client chỉ gửi ý định, in kết quả, báo máu tối đa mới và hỏi lại túi.
 */
final class EquipmentPresetCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/bodo";
    private static final String COMMAND_EN = "/preset";
    private static final String OUTCOME_PREFIX = "EQUIPMENT_PRESET_OUTCOME_";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final ItemCatalogCoordinator itemCatalog;
    private final IntConsumer onOwnMaxHpChanged;
    private final I18NBundle i18n = KageI18n.load();

    EquipmentPresetCoordinator(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        this.sender = sender;
        this.chat = feeds.chat();
        this.itemCatalog = feeds.itemCatalog();
        this.onOwnMaxHpChanged = feeds.onOwnMaxHpChanged();
        chat.addSlashCommands(this::onSlash);
    }

    /** Ý định đọc từ một dòng chat (đã biết là lệnh này). */
    sealed interface Intent {
        /** Liệt kê. */
        record List() implements Intent {}

        /** Lưu đồ đang mặc vào bộ. */
        record Save(int presetNo) implements Intent {}

        /** Mặc cả bộ. */
        record Apply(int presetNo) implements Intent {}

        /** Sai cú pháp. */
        record Usage() implements Intent {}
    }

    /** Đọc đối số sau tên lệnh — tĩnh để test không cần màn hình. */
    static Intent parse(String arguments) {
        String[] words = arguments.strip().toLowerCase(Locale.ROOT).split("\\s+", 3);
        if (words.length == 1 && words[0].isEmpty()) {
            return new Intent.List();
        }
        boolean save = "luu".equals(words[0]) || "lưu".equals(words[0]) || "save".equals(words[0]);
        boolean wear = "mac".equals(words[0]) || "mặc".equals(words[0]) || "wear".equals(words[0]);
        String number = save || wear ? (words.length == 2 ? words[1] : "") : (words.length == 1 ? words[0] : "");
        if (!number.matches("[1-9]")) {
            return new Intent.Usage();
        }
        int presetNo = Integer.parseInt(number);
        return save ? new Intent.Save(presetNo) : new Intent.Apply(presetNo);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!COMMAND_VI.equals(command) && !COMMAND_EN.equals(command)) {
            return false;
        }
        switch (parse(words.length > 1 ? words[1] : "")) {
            case Intent.List() ->
                sender.sendReadRequest(
                        Opcode.OPCODE_GET_EQUIPMENT_PRESETS_REQUEST_VALUE,
                        GetEquipmentPresetsRequest.getDefaultInstance());
            case Intent.Save save ->
                sender.sendReadRequest(
                        Opcode.OPCODE_SAVE_EQUIPMENT_PRESET_COMMAND_VALUE,
                        SaveEquipmentPresetCommand.newBuilder()
                                .setPresetNo(save.presetNo())
                                .build());
            case Intent.Apply apply ->
                sender.sendReadRequest(
                        Opcode.OPCODE_APPLY_EQUIPMENT_PRESET_COMMAND_VALUE,
                        ApplyEquipmentPresetCommand.newBuilder()
                                .setPresetNo(apply.presetNo())
                                .build());
            case Intent.Usage() -> chat.showSystemNotice(KageI18n.textOrKey(i18n, "preset.usage"));
        }
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_GET_EQUIPMENT_PRESETS_RESPONSE_VALUE) {
            GetEquipmentPresetsResponse response = GetEquipmentPresetsResponse.parseFrom(frame.payload());
            if (response.getPresetsCount() == 0) {
                chat.showSystemNotice(KageI18n.textOrKey(i18n, "preset.none"));
            }
            response.getPresetsList().forEach(preset -> chat.showSystemNotice(describe(preset)));
            return true;
        }
        if (opcode == Opcode.OPCODE_SAVE_EQUIPMENT_PRESET_RESPONSE_VALUE) {
            SaveEquipmentPresetResponse response = SaveEquipmentPresetResponse.parseFrom(frame.payload());
            chat.showSystemNotice(
                    response.getOutcome() == EquipmentPresetOutcome.EQUIPMENT_PRESET_OUTCOME_OK
                            ? KageI18n.formatOrKey(i18n, "preset.saved", describe(response.getPreset()))
                            : outcomeText(i18n, response.getOutcome()));
            return true;
        }
        if (opcode == Opcode.OPCODE_APPLY_EQUIPMENT_PRESET_RESPONSE_VALUE) {
            onApplied(ApplyEquipmentPresetResponse.parseFrom(frame.payload()));
            return true;
        }
        return false;
    }

    private void onApplied(ApplyEquipmentPresetResponse response) {
        chat.showSystemNotice(describeApply(i18n, response));
        if (response.getMaxHp() > 0) {
            onOwnMaxHpChanged.accept(response.getMaxHp());
        }
        if (response.getEquipped() > 0) {
            sender.listInventory();
        }
    }

    /** Một dòng "Bộ 1: Trường kiếm +3, Áo vải, ..." (món không còn ghi "(mất)"). */
    private String describe(EquipmentPresetView preset) {
        StringBuilder items = new StringBuilder();
        for (EquipmentPresetSlot slot : preset.getSlotsList()) {
            if (!items.isEmpty()) {
                items.append(", ");
            }
            items.append(
                    slot.getItemTemplateId() == 0
                            ? KageI18n.textOrKey(i18n, "preset.item_gone")
                            : itemCatalog
                                    .nameKey(slot.getItemTemplateId())
                                    .map(key -> KageI18n.textOrKey(i18n, key))
                                    .orElseGet(() -> "#" + slot.getItemTemplateId()));
        }
        return KageI18n.formatOrKey(i18n, "preset.line", preset.getPresetNo(), items);
    }

    /** Dòng kết quả mặc bộ — tĩnh để test không cần màn hình. */
    static String describeApply(I18NBundle i18n, ApplyEquipmentPresetResponse response) {
        if (response.getOutcome() != EquipmentPresetOutcome.EQUIPMENT_PRESET_OUTCOME_OK) {
            return outcomeText(i18n, response.getOutcome());
        }
        return response.getMissing() == 0
                ? KageI18n.formatOrKey(i18n, "preset.applied", response.getEquipped())
                : KageI18n.formatOrKey(i18n, "preset.applied_missing", response.getEquipped(), response.getMissing());
    }

    private static String outcomeText(I18NBundle i18n, EquipmentPresetOutcome outcome) {
        String suffix = outcome == EquipmentPresetOutcome.UNRECOGNIZED
                ? "unspecified"
                : outcome.name().substring(OUTCOME_PREFIX.length()).toLowerCase(Locale.ROOT);
        return KageI18n.textOrKey(i18n, "preset.outcome." + suffix);
    }
}

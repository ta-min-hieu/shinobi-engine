package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ChooseEncounterOptionCommand;
import dev.kage.protocol.ChooseEncounterOptionResponse;
import dev.kage.protocol.EncounterChoiceOutcome;
import dev.kage.protocol.EncounterOptionView;
import dev.kage.protocol.EncounterStartedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Kỳ ngộ (ADR-121) — server mở cửa sổ (gói 324) khi vừa hạ quái mà gặp kỳ ngộ: dòng trạng thái là câu chuyện, mỗi lựa
 * chọn một dòng có nút "Chọn". Kết quả (thưởng, quái xuất hiện) do server quyết; client chỉ đọc lại.
 */
final class EncounterCoordinator implements WindowFlow {

    private final ReportPanel panel;
    private final WorldCommandSender sender;
    private final ItemCatalogCoordinator itemCatalog;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final List<Integer> rowOptions = new ArrayList<>();
    private long instanceId;

    EncounterCoordinator(ReportPanel panel, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        this.panel = panel;
        this.sender = sender;
        this.itemCatalog = feeds.itemCatalog();
        this.chat = feeds.chat();
        panel.setCanOpen(() -> false);
        panel.setOnRowAction(this::onRowAction);
    }

    private void onRowAction(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= rowOptions.size()) {
            return;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_CHOOSE_ENCOUNTER_OPTION_COMMAND_VALUE,
                ChooseEncounterOptionCommand.newBuilder()
                        .setInstanceId(instanceId)
                        .setOptionIndex(rowOptions.get(rowIndex))
                        .build());
    }

    @Override
    public boolean isOpen() {
        return panel.isOpen();
    }

    @Override
    public void close() {
        panel.close();
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_ENCOUNTER_STARTED_EVENT_VALUE) {
            show(EncounterStartedEvent.parseFrom(frame.payload()));
            return true;
        }
        if (opcode == Opcode.OPCODE_CHOOSE_ENCOUNTER_OPTION_RESPONSE_VALUE) {
            settle(ChooseEncounterOptionResponse.parseFrom(frame.payload()));
            return true;
        }
        return false;
    }

    private void show(EncounterStartedEvent event) {
        instanceId = event.getInstanceId();
        rowOptions.clear();
        List<ReportPanel.Row> rows = new ArrayList<>();
        for (EncounterOptionView option : event.getOptionsList()) {
            rows.add(new ReportPanel.Row(text(option.getLabelKey()), false, "encounter.action.choose"));
            rowOptions.add(option.getIndex());
        }
        panel.setRows(rows);
        panel.setStatus(text(event.getPromptKey()));
        if (!panel.isOpen()) {
            panel.show();
        }
        chat.showSystemNotice(text("encounter.notice.started"));
    }

    private void settle(ChooseEncounterOptionResponse response) {
        if (response.getOutcome() != EncounterChoiceOutcome.ENCOUNTER_CHOICE_OUTCOME_OK) {
            String suffix = response.getOutcome()
                    .name()
                    .substring("ENCOUNTER_CHOICE_OUTCOME_".length())
                    .toLowerCase(Locale.ROOT);
            panel.setStatus(text("encounter.outcome." + suffix));
            return;
        }
        StringBuilder result = new StringBuilder(text(response.getResultKey()));
        if (response.getGold() > 0) {
            result.append(' ').append(KageI18n.formatOrKey(i18n, "encounter.reward.gold", response.getGold()));
        }
        if (response.getItemTemplateId() != 0) {
            String name = itemCatalog
                    .nameKey(response.getItemTemplateId())
                    .map(this::text)
                    .orElseGet(() -> "#" + response.getItemTemplateId());
            result.append(' ')
                    .append(KageI18n.formatOrKey(i18n, "encounter.reward.item", name, response.getQuantity()));
            sender.listInventory(); // túi vừa đổi
        }
        rowOptions.clear();
        panel.setRows(List.of());
        panel.setStatus(result.toString());
        chat.showSystemNotice(result.toString());
    }

    private String text(String key) {
        return KageI18n.textOrKey(i18n, key);
    }

    @Override
    public void reset() {
        panel.close();
        rowOptions.clear();
        instanceId = 0L;
        panel.setRows(List.of());
    }
}

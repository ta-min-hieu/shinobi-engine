package dev.kage.client.core.world;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.client.core.ui.theme.KageSkin;
import dev.kage.protocol.LootRollChoice;
import dev.kage.protocol.LootRollChoiceCommand;
import dev.kage.protocol.LootRollEntry;
import dev.kage.protocol.LootRollResultEvent;
import dev.kage.protocol.LootRollStartedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Tung xúc xắc chia đồ nhóm (ADR-150) — server mở cửa sổ (gói 1328) khi một món trang bị rơi lúc đánh cùng nhóm: ba
 * dòng Cần / Tham / Bỏ. Nhiều món cùng lúc thì xếp hàng, chọn xong món này mới hiện món sau. Kết quả (gói 1331) in
 * thành dòng hệ thống — server tung và quyết người nhận, client chỉ đọc lại.
 */
final class LootRollCoordinator implements WindowFlow {

    /** Tối đa số lượt tung xếp hàng — gói dị dạng không được kéo theo hàng đợi vô hạn. */
    private static final int MAX_QUEUED = 16;

    private static final List<LootRollChoice> ROW_CHOICES = List.of(
            LootRollChoice.LOOT_ROLL_CHOICE_NEED,
            LootRollChoice.LOOT_ROLL_CHOICE_GREED,
            LootRollChoice.LOOT_ROLL_CHOICE_PASS);

    private final ReportPanel panel;
    private final WorldCommandSender sender;
    private final ItemCatalogCoordinator itemCatalog;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final Deque<LootRollStartedEvent> queue = new ArrayDeque<>();

    LootRollCoordinator(ReportPanel panel, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        this.panel = panel;
        this.sender = sender;
        this.itemCatalog = feeds.itemCatalog();
        this.chat = feeds.chat();
        panel.setCanOpen(() -> false);
        panel.setOnRowAction(this::onRowAction);
    }

    static LootRollCoordinator create(KageSkin skin, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new LootRollCoordinator(
                new ReportPanel(skin, "loot.title", Input.Keys.UNKNOWN, List.of()), sender, feeds);
    }

    ReportPanel panel() {
        return panel;
    }

    private void onRowAction(int rowIndex) {
        LootRollStartedEvent current = queue.peekFirst();
        if (current == null || rowIndex < 0 || rowIndex >= ROW_CHOICES.size()) {
            return;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_LOOT_ROLL_CHOICE_COMMAND_VALUE,
                LootRollChoiceCommand.newBuilder()
                        .setRollId(current.getRollId())
                        .setChoice(ROW_CHOICES.get(rowIndex))
                        .build());
        queue.pollFirst();
        showHead();
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
        if (opcode == Opcode.OPCODE_LOOT_ROLL_STARTED_EVENT_VALUE) {
            if (queue.size() < MAX_QUEUED) {
                queue.addLast(LootRollStartedEvent.parseFrom(frame.payload()));
            }
            showHead();
            return true;
        }
        if (opcode == Opcode.OPCODE_LOOT_ROLL_RESULT_EVENT_VALUE) {
            LootRollResultEvent result = LootRollResultEvent.parseFrom(frame.payload());
            queue.removeIf(pending -> pending.getRollId() == result.getRollId());
            chat.showSystemNotice(describe(result));
            showHead();
            return true;
        }
        return opcode == Opcode.OPCODE_LOOT_ROLL_CHOICE_RESPONSE_VALUE; // kết quả thật tới bằng 1331
    }

    private void showHead() {
        LootRollStartedEvent head = queue.peekFirst();
        if (head == null) {
            panel.close();
            return;
        }
        panel.setStatus(KageI18n.formatOrKey(
                i18n, "loot.prompt", itemName(head.getItemTemplateId()), head.getQuantity(), head.getSeconds()));
        panel.setRows(List.of(
                new ReportPanel.Row(text("loot.choice.need"), false, "loot.action.need"),
                new ReportPanel.Row(text("loot.choice.greed"), false, "loot.action.greed"),
                new ReportPanel.Row(text("loot.choice.pass"), false, "loot.action.pass")));
        if (!panel.isOpen()) {
            panel.show();
        }
    }

    private String describe(LootRollResultEvent result) {
        StringBuilder line = new StringBuilder(KageI18n.formatOrKey(
                i18n,
                result.getByMail() ? "loot.result.mail" : "loot.result",
                result.getWinnerName(),
                itemName(result.getItemTemplateId()),
                result.getQuantity()));
        for (LootRollEntry entry : result.getEntriesList()) {
            if (entry.getRoll() > 0) {
                line.append(' ').append(KageI18n.formatOrKey(i18n, "loot.entry", entry.getName(), entry.getRoll()));
            }
        }
        return line.toString();
    }

    private String itemName(long templateId) {
        return itemCatalog.nameKey(templateId).map(this::text).orElseGet(() -> "#" + templateId);
    }

    private String text(String key) {
        return KageI18n.textOrKey(i18n, key);
    }

    @Override
    public void reset() {
        queue.clear();
        panel.close();
        panel.setRows(List.of());
    }
}

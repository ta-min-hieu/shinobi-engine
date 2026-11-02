package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.client.core.ui.theme.KageSkin;
import dev.kage.protocol.GetLuckyWheelRequest;
import dev.kage.protocol.GetLuckyWheelResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SpinLuckyWheelCommand;
import dev.kage.protocol.SpinLuckyWheelOutcome;
import dev.kage.protocol.SpinLuckyWheelResponse;
import dev.kage.protocol.WheelPrizeView;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Vòng quay may mắn (ADR-151) — cửa sổ mở bằng lệnh chat {@code /vongquay} (hoặc {@code /wheel}). Dòng đầu có nút
 * "Quay" kèm số lượt còn lại; các dòng sau là các ô thưởng. Server chọn ô, trừ tiền, trao thưởng — client chỉ đọc lại.
 */
final class LuckyWheelCoordinator implements WindowFlow {

    private static final List<String> COMMANDS = List.of("/vongquay", "/wheel");
    private static final String OUTCOME_PREFIX = "SPIN_LUCKY_WHEEL_OUTCOME_";
    /** Vòng thật có 8 ô — gói dị dạng không được kéo theo hàng nghìn dòng. */
    private static final int MAX_PRIZES = 16;

    private final ReportPanel panel;
    private final WorldCommandSender sender;
    private final ItemCatalogCoordinator itemCatalog;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final List<WheelPrizeView> prizes = new ArrayList<>();
    private int freeLeft;
    private int paidLeft;
    private long cost;

    LuckyWheelCoordinator(ReportPanel panel, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        this.panel = panel;
        this.sender = sender;
        this.itemCatalog = feeds.itemCatalog();
        this.chat = feeds.chat();
        panel.setOnRefresh(this::request);
        panel.setOnRowAction(this::onRowAction);
        panel.setStatus(text("wheel.hint"));
        chat.addSlashCommands(this::onSlash);
    }

    static LuckyWheelCoordinator create(KageSkin skin, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new LuckyWheelCoordinator(
                new ReportPanel(skin, "wheel.title", Input.Keys.UNKNOWN, List.of()), sender, feeds);
    }

    ReportPanel panel() {
        return panel;
    }

    private boolean onSlash(String line) {
        if (!COMMANDS.contains(line.strip().toLowerCase(Locale.ROOT))) {
            return false;
        }
        panel.open();
        return true;
    }

    private void request() {
        sender.sendReadRequest(Opcode.OPCODE_GET_LUCKY_WHEEL_REQUEST_VALUE, GetLuckyWheelRequest.getDefaultInstance());
    }

    private void onRowAction(int rowIndex) {
        if (rowIndex == 0) {
            sender.sendReadRequest(
                    Opcode.OPCODE_SPIN_LUCKY_WHEEL_COMMAND_VALUE, SpinLuckyWheelCommand.getDefaultInstance());
        }
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
        if (opcode == Opcode.OPCODE_GET_LUCKY_WHEEL_RESPONSE_VALUE) {
            GetLuckyWheelResponse wheel = GetLuckyWheelResponse.parseFrom(frame.payload());
            prizes.clear();
            wheel.getPrizesList().stream().limit(MAX_PRIZES).forEach(prizes::add);
            freeLeft = wheel.getFreeSpinsLeft();
            paidLeft = wheel.getPaidSpinsLeft();
            cost = wheel.getSpinCost();
            showRows();
            return true;
        }
        if (opcode == Opcode.OPCODE_SPIN_LUCKY_WHEEL_RESPONSE_VALUE) {
            settle(SpinLuckyWheelResponse.parseFrom(frame.payload()));
            return true;
        }
        return false;
    }

    private void settle(SpinLuckyWheelResponse response) {
        freeLeft = response.getFreeSpinsLeft();
        paidLeft = response.getPaidSpinsLeft();
        if (response.getOutcome() == SpinLuckyWheelOutcome.SPIN_LUCKY_WHEEL_OUTCOME_OK) {
            String line = KageI18n.formatOrKey(i18n, "wheel.result", describe(response.getPrize()));
            panel.setStatus(line);
            chat.showSystemNotice(line);
            if (response.getPrize().getItemTemplateId() != 0) {
                sender.listInventory(); // túi vừa đổi (linh thạch tới riêng qua GOLD_CHANGED_EVENT)
            }
        } else {
            String suffix = response.getOutcome() == SpinLuckyWheelOutcome.UNRECOGNIZED
                    ? "unspecified"
                    : response.getOutcome()
                            .name()
                            .substring(OUTCOME_PREFIX.length())
                            .toLowerCase(Locale.ROOT);
            panel.setStatus(text("wheel.outcome." + suffix));
        }
        showRows();
    }

    private void showRows() {
        List<ReportPanel.Row> rows = new ArrayList<>();
        String spinLabel = freeLeft > 0
                ? KageI18n.formatOrKey(i18n, "wheel.spin.free", freeLeft)
                : KageI18n.formatOrKey(i18n, "wheel.spin.paid", cost, paidLeft);
        rows.add(new ReportPanel.Row(spinLabel, true, "wheel.action.spin"));
        for (WheelPrizeView prize : prizes) {
            rows.add(new ReportPanel.Row(describe(prize), false));
        }
        panel.setRows(rows);
    }

    /** "Túi linh thạch nhỏ — 200 linh thạch" / "... — Hồi Khí Đan x2". */
    private String describe(WheelPrizeView prize) {
        String reward = prize.getItemTemplateId() != 0
                ? KageI18n.formatOrKey(
                        i18n, "wheel.reward.item", itemName(prize.getItemTemplateId()), prize.getQuantity())
                : KageI18n.formatOrKey(i18n, "wheel.reward.gold", prize.getGold());
        return text(prize.getLabelKey()) + " — " + reward;
    }

    private String itemName(long templateId) {
        return itemCatalog.nameKey(templateId).map(this::text).orElseGet(() -> "#" + templateId);
    }

    private String text(String key) {
        return KageI18n.textOrKey(i18n, key);
    }

    @Override
    public void reset() {
        panel.close();
        prizes.clear();
        panel.setRows(List.of());
        panel.setStatus(text("wheel.hint"));
    }
}

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
import dev.kage.protocol.GetJunkPreviewRequest;
import dev.kage.protocol.GetJunkPreviewResponse;
import dev.kage.protocol.JunkLineView;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SellJunkCommand;
import dev.kage.protocol.SellJunkResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Bán nhanh đồ rác (ADR-170) — cửa sổ mở bằng lệnh chat {@code /banrac} (hoặc {@code /selljunk}) hay nút "Bán rác"
 * trong túi đồ. Liệt kê TRƯỚC những gì sẽ bán (món khoá/đang mặc không có trong danh sách) và tổng vàng; chỉ bán khi
 * người chơi bấm "Bán hết". Server tự tính lại lúc bán.
 */
final class JunkSaleCoordinator implements WindowFlow {

    private static final List<String> COMMANDS = List.of("/banrac", "/selljunk");
    /** Ít loại rác — gói dị dạng không được kéo theo hàng nghìn dòng. */
    private static final int MAX_ROWS = 30;

    private final ReportPanel panel;
    private final WorldCommandSender sender;
    private final ItemCatalogCoordinator itemCatalog;
    private final I18NBundle i18n = KageI18n.load();
    private boolean hasJunk;

    JunkSaleCoordinator(ReportPanel panel, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        this.panel = panel;
        this.sender = sender;
        this.itemCatalog = feeds.itemCatalog();
        panel.setOnRefresh(this::request);
        panel.setOnRowAction(this::onRowAction);
        panel.setStatus(text("junk.hint"));
        feeds.chat().addSlashCommands(this::onSlash);
    }

    static JunkSaleCoordinator create(KageSkin skin, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new JunkSaleCoordinator(
                new ReportPanel(skin, "junk.title", Input.Keys.UNKNOWN, List.of()), sender, feeds);
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
        sender.sendReadRequest(
                Opcode.OPCODE_GET_JUNK_PREVIEW_REQUEST_VALUE, GetJunkPreviewRequest.getDefaultInstance());
    }

    /** Dòng đầu là dòng "Bán hết" — dòng khác chỉ để đọc. */
    private void onRowAction(int rowIndex) {
        if (rowIndex == 0 && hasJunk) {
            sender.sendReadRequest(Opcode.OPCODE_SELL_JUNK_COMMAND_VALUE, SellJunkCommand.getDefaultInstance());
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
        if (opcode == Opcode.OPCODE_GET_JUNK_PREVIEW_RESPONSE_VALUE) {
            show(GetJunkPreviewResponse.parseFrom(frame.payload()).getLinesList());
            if (!panel.isOpen()) {
                panel.open(); // mở từ nút "Bán rác" trong túi đồ
            }
            return true;
        }
        if (opcode != Opcode.OPCODE_SELL_JUNK_RESPONSE_VALUE) {
            return false;
        }
        SellJunkResponse response = SellJunkResponse.parseFrom(frame.payload());
        panel.setStatus(describe(response));
        show(List.of());
        sender.listInventory();
        return true;
    }

    /** Dòng trạng thái sau khi bán — tách ra để test không cần màn hình. */
    String describe(SellJunkResponse response) {
        if (response.getGoldFull()) {
            return text("junk.gold_full");
        }
        int pieces = response.getSoldList().stream()
                .mapToInt(JunkLineView::getQuantity)
                .sum();
        return pieces == 0 ? text("junk.nothing") : KageI18n.formatOrKey(i18n, "junk.sold", pieces, response.getGold());
    }

    private void show(List<JunkLineView> lines) {
        List<ReportPanel.Row> rows = new ArrayList<>();
        long total = 0L;
        for (JunkLineView line : lines) {
            total += line.getPriceEach() * line.getQuantity();
        }
        hasJunk = !lines.isEmpty();
        rows.add(
                hasJunk
                        ? new ReportPanel.Row(
                                KageI18n.formatOrKey(i18n, "junk.total", total), true, "junk.action.sell_all")
                        : new ReportPanel.Row(text("junk.nothing"), false));
        for (JunkLineView line : lines) {
            if (rows.size() >= MAX_ROWS) {
                break;
            }
            rows.add(new ReportPanel.Row(
                    KageI18n.formatOrKey(
                            i18n,
                            "junk.row",
                            nameOf(line.getItemTemplateId()),
                            line.getQuantity(),
                            line.getPriceEach()),
                    false));
        }
        panel.setRows(rows);
    }

    private String nameOf(long itemTemplateId) {
        return itemCatalog.nameKey(itemTemplateId).map(this::text).orElseGet(() -> "#" + itemTemplateId);
    }

    private String text(String key) {
        return KageI18n.textOrKey(i18n, key);
    }

    @Override
    public void reset() {
        panel.close();
        hasJunk = false;
        panel.setRows(List.of());
    }
}

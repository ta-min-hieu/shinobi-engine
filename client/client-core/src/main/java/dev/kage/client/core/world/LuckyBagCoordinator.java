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
import dev.kage.protocol.GetLuckyBagsRequest;
import dev.kage.protocol.GetLuckyBagsResponse;
import dev.kage.protocol.LuckyBagRewardView;
import dev.kage.protocol.LuckyBagView;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.OpenLuckyBagCommand;
import dev.kage.protocol.OpenLuckyBagOutcome;
import dev.kage.protocol.OpenLuckyBagResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Túi phúc (ADR-163) — cửa sổ mở bằng lệnh chat {@code /tuiphuc} (hoặc {@code /luckybag}). Mỗi loại túi một dòng (đang
 * có bao nhiêu) kèm nút "Mở", rồi bảng quà với tỉ lệ công khai. Server rút và cấp quà; client chỉ bày.
 */
final class LuckyBagCoordinator implements WindowFlow {

    private static final List<String> COMMANDS = List.of("/tuiphuc", "/luckybag");
    private static final String OUTCOME_PREFIX = "OPEN_LUCKY_BAG_OUTCOME_";
    private static final int PERCENT = 100;
    /** Bảng thật có một túi mười quà — gói dị dạng không được kéo theo hàng nghìn dòng. */
    private static final int MAX_ROWS = 40;

    private final ReportPanel panel;
    private final WorldCommandSender sender;
    private final ItemCatalogCoordinator itemCatalog;
    private final I18NBundle i18n = KageI18n.load();
    /** Túi của từng dòng có nút "Mở"; dòng quà (không nút) giữ 0. */
    private final List<Long> rowBags = new ArrayList<>();

    LuckyBagCoordinator(ReportPanel panel, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        this.panel = panel;
        this.sender = sender;
        this.itemCatalog = feeds.itemCatalog();
        panel.setOnRefresh(this::request);
        panel.setOnRowAction(this::onRowAction);
        panel.setStatus(text("lucky_bag.hint"));
        feeds.chat().addSlashCommands(this::onSlash);
    }

    static LuckyBagCoordinator create(KageSkin skin, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new LuckyBagCoordinator(
                new ReportPanel(skin, "lucky_bag.title", Input.Keys.UNKNOWN, List.of()), sender, feeds);
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
        sender.sendReadRequest(Opcode.OPCODE_GET_LUCKY_BAGS_REQUEST_VALUE, GetLuckyBagsRequest.getDefaultInstance());
    }

    private void onRowAction(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= rowBags.size() || rowBags.get(rowIndex) == 0L) {
            return;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_OPEN_LUCKY_BAG_COMMAND_VALUE,
                OpenLuckyBagCommand.newBuilder()
                        .setBagItemTemplateId(rowBags.get(rowIndex))
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
        if (opcode == Opcode.OPCODE_GET_LUCKY_BAGS_RESPONSE_VALUE) {
            show(GetLuckyBagsResponse.parseFrom(frame.payload()));
            return true;
        }
        if (opcode != Opcode.OPCODE_OPEN_LUCKY_BAG_RESPONSE_VALUE) {
            return false;
        }
        OpenLuckyBagResponse response = OpenLuckyBagResponse.parseFrom(frame.payload());
        show(response.getBags());
        panel.setStatus(describe(response));
        if (response.getOutcome() == OpenLuckyBagOutcome.OPEN_LUCKY_BAG_OUTCOME_OK) {
            sender.listInventory(); // túi vừa mở ra, quà vừa vào
        }
        return true;
    }

    /** Dòng trạng thái sau một lần mở — tách ra để test không cần màn hình. */
    String describe(OpenLuckyBagResponse response) {
        OpenLuckyBagOutcome outcome = response.getOutcome();
        if (outcome == OpenLuckyBagOutcome.OPEN_LUCKY_BAG_OUTCOME_OK) {
            return KageI18n.formatOrKey(i18n, "lucky_bag.outcome.ok", rewardText(response.getReward()));
        }
        String suffix = outcome == OpenLuckyBagOutcome.UNRECOGNIZED
                ? "unspecified"
                : outcome.name().substring(OUTCOME_PREFIX.length()).toLowerCase(Locale.ROOT);
        return text("lucky_bag.outcome." + suffix);
    }

    private void show(GetLuckyBagsResponse bags) {
        rowBags.clear();
        List<ReportPanel.Row> rows = new ArrayList<>();
        for (LuckyBagView bag : bags.getBagsList()) {
            if (rows.size() >= MAX_ROWS) {
                break;
            }
            rows.add(new ReportPanel.Row(
                    KageI18n.formatOrKey(i18n, "lucky_bag.row", nameOf(bag.getBagItemTemplateId()), bag.getOwned()),
                    bag.getOwned() > 0,
                    "lucky_bag.action.open"));
            rowBags.add(bag.getBagItemTemplateId());
            int total = bag.getRewardsList().stream()
                    .mapToInt(LuckyBagRewardView::getWeight)
                    .sum();
            for (LuckyBagRewardView reward : bag.getRewardsList()) {
                if (rows.size() >= MAX_ROWS) {
                    break;
                }
                int percent = total <= 0 ? 0 : reward.getWeight() * PERCENT / total;
                rows.add(new ReportPanel.Row(
                        KageI18n.formatOrKey(i18n, "lucky_bag.reward_row", percent, rewardText(reward)), false));
                rowBags.add(0L);
            }
        }
        panel.setRows(rows);
    }

    private String rewardText(LuckyBagRewardView reward) {
        if (reward.getItemTemplateId() == 0L) {
            return KageI18n.formatOrKey(i18n, "lucky_bag.gold", reward.getGold());
        }
        return nameOf(reward.getItemTemplateId()) + " x" + reward.getQuantity();
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
        rowBags.clear();
        panel.setRows(List.of());
    }
}

package dev.kage.client.core.world;

import java.util.List;
import java.util.Locale;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.client.core.ui.theme.KageSkin;
import dev.kage.protocol.GetTreasureHuntRequest;
import dev.kage.protocol.GetTreasureHuntResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.TreasureAction;
import dev.kage.protocol.TreasureHuntCommand;
import dev.kage.protocol.TreasureHuntResponse;
import dev.kage.protocol.TreasureOutcome;
import dev.kage.protocol.WorldKind;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Tàng Bảo Đồ (ADR-147) — cửa sổ mở bằng lệnh chat {@code /tangbaodo} (hoặc {@code /treasure}). Hai dòng có nút: đọc
 * một tấm bản đồ, đào ở chỗ đang đứng. Dòng trạng thái ghi điểm chôn, toạ độ X và khoảng cách server tính; điểm chôn,
 * tầm đào và phần thưởng đều do server quyết — client chỉ đọc lại.
 */
final class TreasureCoordinator implements WindowFlow {

    private static final List<String> COMMANDS = List.of("/tangbaodo", "/treasure");
    private static final int ROW_READ = 0;
    private static final int ROW_DIG = 1;
    private static final String OUTCOME_PREFIX = "TREASURE_OUTCOME_";

    private final ReportPanel panel;
    private final WorldCommandSender sender;
    private final ItemCatalogCoordinator itemCatalog;
    private final ChatCoordinator chat;
    private final TreasureMarker marker = new TreasureMarker();
    private final I18NBundle i18n = KageI18n.load();

    /** Cửa sổ riêng — không phím, mở bằng lệnh chat. */
    static TreasureCoordinator create(KageSkin skin, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new TreasureCoordinator(
                new ReportPanel(skin, "treasure.title", Input.Keys.UNKNOWN, List.of()), sender, feeds);
    }

    TreasureCoordinator(ReportPanel panel, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        this.panel = panel;
        this.sender = sender;
        this.itemCatalog = feeds.itemCatalog();
        this.chat = feeds.chat();
        panel.setOnRefresh(this::refresh);
        panel.setOnRowAction(this::onRowAction);
        panel.setRows(List.of(
                new ReportPanel.Row(text("treasure.row.read"), false, "treasure.action.read"),
                new ReportPanel.Row(text("treasure.row.dig"), false, "treasure.action.dig")));
        panel.setStatus(text("treasure.hint"));
        feeds.chat().addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        if (!COMMANDS.contains(line.strip().toLowerCase(Locale.ROOT))) {
            return false;
        }
        panel.open();
        return true;
    }

    private void onRowAction(int rowIndex) {
        if (rowIndex == ROW_READ) {
            send(TreasureAction.TREASURE_ACTION_READ);
        } else if (rowIndex == ROW_DIG) {
            send(TreasureAction.TREASURE_ACTION_DIG);
        }
    }

    private void send(TreasureAction action) {
        sender.sendReadRequest(
                Opcode.OPCODE_TREASURE_HUNT_COMMAND_VALUE,
                TreasureHuntCommand.newBuilder().setAction(action).build());
    }

    /** Dấu chỗ chôn để {@code WorldScreen} vẽ. */
    TreasureMarker marker() {
        return marker;
    }

    /** Hỏi cuộc tìm đang mở — lúc vào world, để dấu X hiện lại sau khi đăng nhập. */
    void refresh() {
        // D9 — opcode chỉ đọc: được gọi cả từ gói đến (đổi World), nên không được là lệnh 341.
        sender.sendReadRequest(
                Opcode.OPCODE_GET_TREASURE_HUNT_REQUEST_VALUE, GetTreasureHuntRequest.getDefaultInstance());
    }

    /** Dấu X chỉ có nghĩa ở world chính: rời đi thì ẩn, quay về thì hỏi lại server. */
    void onWorldChanged(WorldKind kind) {
        marker.hide();
        if (kind == WorldKind.WORLD_KIND_MAIN) {
            refresh();
        }
    }

    ReportPanel panel() {
        return panel;
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
        if (opcode == Opcode.OPCODE_GET_TREASURE_HUNT_RESPONSE_VALUE) {
            GetTreasureHuntResponse view = GetTreasureHuntResponse.parseFrom(frame.payload());
            marker.apply(view);
            panel.setStatus(describe(TreasureHuntResponse.newBuilder()
                    .setAction(TreasureAction.TREASURE_ACTION_STATUS)
                    .setOutcome(
                            view.getHunting()
                                    ? TreasureOutcome.TREASURE_OUTCOME_OK
                                    : TreasureOutcome.TREASURE_OUTCOME_NO_HUNT)
                    .setHunting(view.getHunting())
                    .setSpotNameKey(view.getSpotNameKey())
                    .setSpotX(view.getSpotX())
                    .setDistance(view.getDistance())
                    .build()));
            return true;
        }
        if (opcode != Opcode.OPCODE_TREASURE_HUNT_RESPONSE_VALUE) {
            return false;
        }
        TreasureHuntResponse response = TreasureHuntResponse.parseFrom(frame.payload());
        marker.apply(response); // ADR-147 D8 — dấu X ở chỗ chôn
        panel.setStatus(describe(response));
        return true;
    }

    /** Dòng trạng thái cho một phản hồi — gói-thuần, tách ra để test không cần Stage. */
    String describe(TreasureHuntResponse response) {
        StringBuilder status = new StringBuilder();
        TreasureOutcome outcome = response.getOutcome();
        boolean dug = response.getAction() == TreasureAction.TREASURE_ACTION_DIG
                && outcome == TreasureOutcome.TREASURE_OUTCOME_OK;
        if (dug) {
            String reward = reward(response);
            chat.showSystemNotice(reward);
            status.append(reward);
            sender.listInventory(); // túi vừa đổi (linh thạch tới riêng qua GOLD_CHANGED_EVENT)
        } else if (response.getAction() == TreasureAction.TREASURE_ACTION_READ
                && outcome == TreasureOutcome.TREASURE_OUTCOME_OK) {
            status.append(text("treasure.read.ok"));
            sender.listInventory(); // vừa tiêu một tấm bản đồ
        } else if (outcome != TreasureOutcome.TREASURE_OUTCOME_OK) {
            status.append(text("treasure.outcome."
                    + outcome.name().substring(OUTCOME_PREFIX.length()).toLowerCase(Locale.ROOT)));
        }
        if (response.getHunting()) {
            appendLine(
                    status,
                    KageI18n.formatOrKey(
                            i18n,
                            "treasure.hunt.spot",
                            text(response.getSpotNameKey()),
                            Math.round(response.getSpotX())));
            if (response.getDistance() >= 0f) {
                appendLine(
                        status,
                        KageI18n.formatOrKey(i18n, "treasure.hunt.distance", Math.round(response.getDistance())));
            }
        } else if (!dug && outcome == TreasureOutcome.TREASURE_OUTCOME_NO_HUNT) {
            appendLine(status, text("treasure.hint"));
        }
        return status.toString();
    }

    private String reward(TreasureHuntResponse response) {
        StringBuilder result = new StringBuilder(text(response.getResultKey()));
        if (response.getGold() > 0) {
            result.append(' ').append(KageI18n.formatOrKey(i18n, "treasure.reward.gold", response.getGold()));
        }
        if (response.getItemTemplateId() != 0) {
            String name = itemCatalog
                    .nameKey(response.getItemTemplateId())
                    .map(this::text)
                    .orElseGet(() -> "#" + response.getItemTemplateId());
            result.append(' ').append(KageI18n.formatOrKey(i18n, "treasure.reward.item", name, response.getQuantity()));
        }
        if (response.getGuardianSpawned()) {
            result.append(' ').append(text("treasure.reward.guardian"));
        }
        return result.toString();
    }

    private static void appendLine(StringBuilder status, String line) {
        if (!status.isEmpty()) {
            status.append('\n');
        }
        status.append(line);
    }

    private String text(String key) {
        return KageI18n.textOrKey(i18n, key);
    }

    @Override
    public void reset() {
        panel.close();
        marker.hide();
        panel.setStatus(text("treasure.hint"));
    }
}

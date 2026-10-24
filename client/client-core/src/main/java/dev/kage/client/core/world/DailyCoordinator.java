package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ClaimOnlineRewardCommand;
import dev.kage.protocol.ClaimOnlineRewardResponse;
import dev.kage.protocol.ClaimSeclusionCommand;
import dev.kage.protocol.ClaimSeclusionResponse;
import dev.kage.protocol.DismantleItemResponse;
import dev.kage.protocol.GatherHerbCommand;
import dev.kage.protocol.GatherHerbResponse;
import dev.kage.protocol.GetOnlineRewardsRequest;
import dev.kage.protocol.GetOnlineRewardsResponse;
import dev.kage.protocol.GetSeclusionStatusRequest;
import dev.kage.protocol.GetSeclusionStatusResponse;
import dev.kage.protocol.HerbGatheredEvent;
import dev.kage.protocol.HerbNodeView;
import dev.kage.protocol.ListHerbNodesRequest;
import dev.kage.protocol.ListHerbNodesResponse;
import dev.kage.protocol.ListInventoryRequest;
import dev.kage.protocol.OnlineRewardMilestoneView;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Cửa sổ Hằng ngày (phím E, lô 21) — ba tab trên {@link ReportPanel}: Bế quan (ADR-095), Online (ADR-096), Linh thảo
 * (ADR-098). Cũng nhận kết quả phân giải (ADR-097 — nút ở túi đồ) để báo trong chat và làm mới túi. Server tính hết;
 * client chỉ hiển thị.
 */
final class DailyCoordinator implements WindowFlow {

    static final List<String> TAB_KEYS = List.of("daily.tab.seclusion", "daily.tab.online", "daily.tab.herbs");

    private static final int TAB_SECLUSION = 0;
    private static final int TAB_ONLINE = 1;
    private static final int SECONDS_PER_MINUTE = 60;
    private static final int MINUTES_PER_HOUR = 60;

    private final ReportPanel panel;
    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final List<Runnable> rowActions = new ArrayList<>();
    private int tab = TAB_SECLUSION;

    DailyCoordinator(ReportPanel panel, WorldCommandSender sender, ChatCoordinator chat) {
        this.panel = panel;
        this.sender = sender;
        this.chat = chat;
        panel.setOnRefresh(this::request);
        panel.setOnTabSelected(index -> {
            tab = index;
            request();
        });
        panel.setOnRowAction(index -> {
            if (index >= 0 && index < rowActions.size()) {
                rowActions.get(index).run();
            }
        });
    }

    private void request() {
        if (tab == TAB_SECLUSION) {
            send(Opcode.OPCODE_GET_SECLUSION_STATUS_REQUEST_VALUE, GetSeclusionStatusRequest.getDefaultInstance());
        } else if (tab == TAB_ONLINE) {
            send(Opcode.OPCODE_GET_ONLINE_REWARDS_REQUEST_VALUE, GetOnlineRewardsRequest.getDefaultInstance());
        } else {
            send(Opcode.OPCODE_LIST_HERB_NODES_REQUEST_VALUE, ListHerbNodesRequest.getDefaultInstance());
        }
    }

    private void send(int opcode, MessageLite message) {
        sender.sendReadRequest(opcode, message);
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
        byte[] payload = frame.payload();
        if (opcode == Opcode.OPCODE_GET_SECLUSION_STATUS_RESPONSE_VALUE) {
            showSeclusion(GetSeclusionStatusResponse.parseFrom(payload));
        } else if (opcode == Opcode.OPCODE_GET_ONLINE_REWARDS_RESPONSE_VALUE) {
            showOnline(GetOnlineRewardsResponse.parseFrom(payload));
        } else if (opcode == Opcode.OPCODE_LIST_HERB_NODES_RESPONSE_VALUE) {
            showHerbs(ListHerbNodesResponse.parseFrom(payload));
        } else {
            return dispatchOutcome(opcode, payload);
        }
        return true;
    }

    /** Kết quả lệnh — một dòng chat, rồi hỏi lại tab đang mở. */
    private boolean dispatchOutcome(int opcode, byte[] payload) throws InvalidProtocolBufferException {
        String line;
        if (opcode == Opcode.OPCODE_CLAIM_SECLUSION_RESPONSE_VALUE) {
            ClaimSeclusionResponse response = ClaimSeclusionResponse.parseFrom(payload);
            line = KageI18n.formatOrKey(
                    i18n, "daily.seclusion." + suffix(response.getOutcome().name()), response.getExpGained());
        } else if (opcode == Opcode.OPCODE_CLAIM_ONLINE_REWARD_RESPONSE_VALUE) {
            line = text("daily.online."
                    + suffix(ClaimOnlineRewardResponse.parseFrom(payload)
                            .getOutcome()
                            .name()));
        } else if (opcode == Opcode.OPCODE_GATHER_HERB_RESPONSE_VALUE) {
            line = text("daily.herb."
                    + suffix(GatherHerbResponse.parseFrom(payload).getOutcome().name()));
        } else if (opcode == Opcode.OPCODE_HERB_GATHERED_EVENT_VALUE) {
            HerbGatheredEvent event = HerbGatheredEvent.parseFrom(payload);
            line = KageI18n.formatOrKey(
                    i18n, "daily.herb." + suffix(event.getOutcome().name()), event.getQuantity());
        } else if (opcode == Opcode.OPCODE_DISMANTLE_ITEM_RESPONSE_VALUE) {
            DismantleItemResponse response = DismantleItemResponse.parseFrom(payload);
            line = KageI18n.formatOrKey(
                    i18n,
                    "daily.dismantle." + suffix(response.getOutcome().name()),
                    response.getGold(),
                    response.getHerbQuantity());
            send(Opcode.OPCODE_LIST_INVENTORY_REQUEST_VALUE, ListInventoryRequest.getDefaultInstance());
        } else {
            return false;
        }
        chat.showSystemNotice(line);
        if (panel.isOpen()) {
            request();
        }
        return true;
    }

    private void showSeclusion(GetSeclusionStatusResponse status) {
        int minutes = status.getBankedMinutes();
        panel.setStatus(text("daily.hint.seclusion"));
        List<ReportPanel.Row> rows = new ArrayList<>();
        rowActions.clear();
        add(
                rows,
                KageI18n.formatOrKey(i18n, "daily.row.banked", minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR),
                "",
                () -> {});
        add(
                rows,
                KageI18n.formatOrKey(i18n, "daily.row.claim_single", status.getExpSingle()),
                "daily.action.claim",
                () -> claimSeclusion(false));
        add(
                rows,
                KageI18n.formatOrKey(i18n, "daily.row.claim_double", status.getExpSingle() * 2, status.getDoubleCost()),
                "daily.action.claim",
                () -> claimSeclusion(true));
        panel.setRows(rows);
    }

    private void showOnline(GetOnlineRewardsResponse status) {
        int minutes = status.getSecondsToday() / SECONDS_PER_MINUTE;
        panel.setStatus(KageI18n.formatOrKey(i18n, "daily.hint.online", minutes));
        List<ReportPanel.Row> rows = new ArrayList<>();
        rowActions.clear();
        for (OnlineRewardMilestoneView milestone : status.getMilestonesList()) {
            String reward = milestone.getItemCode().isEmpty()
                    ? KageI18n.formatOrKey(i18n, "daily.reward.gold", milestone.getGold())
                    : KageI18n.formatOrKey(
                            i18n,
                            "daily.reward.item",
                            milestone.getGold(),
                            text("item." + milestone.getItemCode() + ".name"),
                            milestone.getItemQuantity());
            String state = text(
                    milestone.getClaimed()
                            ? "daily.state.claimed"
                            : milestone.getReached() ? "daily.state.ready" : "daily.state.locked");
            int milestoneMinutes = milestone.getMinutes();
            add(
                    rows,
                    KageI18n.formatOrKey(i18n, "daily.row.milestone", milestoneMinutes, reward, state),
                    milestone.getReached() && !milestone.getClaimed() ? "daily.action.claim" : "",
                    () -> claimOnline(milestoneMinutes));
        }
        panel.setRows(rows);
    }

    private void showHerbs(ListHerbNodesResponse response) {
        panel.setStatus(text("daily.hint.herbs"));
        List<ReportPanel.Row> rows = new ArrayList<>();
        rowActions.clear();
        for (HerbNodeView node : response.getNodesList()) {
            String where = node.getDistance() < 0f
                    ? text("daily.herb.far_away")
                    : KageI18n.formatOrKey(i18n, "daily.herb.distance", Math.round(node.getDistance()));
            String state = node.getReady()
                    ? text("daily.state.ready")
                    : KageI18n.formatOrKey(i18n, "daily.herb.respawn", node.getSecondsUntilReady());
            long nodeId = node.getNodeId();
            add(
                    rows,
                    KageI18n.formatOrKey(
                            i18n,
                            "daily.row.herb",
                            text("item." + node.getItemCode() + ".name"),
                            Math.round(node.getX()),
                            where,
                            state),
                    node.getReady() ? "daily.action.gather" : "",
                    () -> send(
                            Opcode.OPCODE_GATHER_HERB_COMMAND_VALUE,
                            GatherHerbCommand.newBuilder().setNodeId(nodeId).build()));
        }
        panel.setRows(rows);
    }

    private void claimSeclusion(boolean doubled) {
        send(
                Opcode.OPCODE_CLAIM_SECLUSION_COMMAND_VALUE,
                ClaimSeclusionCommand.newBuilder().setDoubled(doubled).build());
    }

    private void claimOnline(int minutes) {
        send(
                Opcode.OPCODE_CLAIM_ONLINE_REWARD_COMMAND_VALUE,
                ClaimOnlineRewardCommand.newBuilder()
                        .setMilestoneMinutes(minutes)
                        .build());
    }

    private void add(List<ReportPanel.Row> rows, String label, String actionKey, Runnable action) {
        rows.add(new ReportPanel.Row(label, false, actionKey));
        rowActions.add(action);
    }

    /** {@code CLAIM_SECLUSION_OUTCOME_OK} → {@code ok}. */
    private static String suffix(String enumName) {
        int at = enumName.indexOf("OUTCOME_");
        return enumName.substring(at < 0 ? 0 : at + "OUTCOME_".length()).toLowerCase(Locale.ROOT);
    }

    private String text(String key) {
        return KageI18n.textOrKey(i18n, key);
    }

    @Override
    public void reset() {
        panel.close();
        panel.setRows(List.of());
    }
}

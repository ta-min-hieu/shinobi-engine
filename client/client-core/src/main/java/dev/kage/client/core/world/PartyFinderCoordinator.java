package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.JoinPartyFinderCommand;
import dev.kage.protocol.JoinPartyFinderOutcome;
import dev.kage.protocol.JoinPartyFinderResponse;
import dev.kage.protocol.ListPartyFinderRequest;
import dev.kage.protocol.ListPartyFinderResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PartyFinderListingView;
import dev.kage.protocol.PartyGoal;
import dev.kage.protocol.PostPartyFinderCommand;
import dev.kage.protocol.PostPartyFinderResponse;
import dev.kage.protocol.RemovePartyFinderCommand;
import dev.kage.protocol.RemovePartyFinderResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Bảng tìm nhóm (ADR-082) — {@link ReportPanel}: bốn dòng "đăng tin" (một mỗi mục tiêu), một dòng "gỡ tin", rồi tin
 * của người khác kèm nút "Vào". Mở từ nút trong cửa sổ Nhóm, không phím riêng. Client không kiểm cấp hay chỗ trống —
 * server kiểm và trả outcome.
 */
final class PartyFinderCoordinator implements WindowFlow {

    private static final List<PartyGoal> GOALS = List.of(
            PartyGoal.PARTY_GOAL_LEVELING,
            PartyGoal.PARTY_GOAL_QUEST,
            PartyGoal.PARTY_GOAL_DUNGEON,
            PartyGoal.PARTY_GOAL_WORLD_BOSS);

    private static final Map<PartyGoal, String> GOAL_KEYS = new EnumMap<>(PartyGoal.class);
    private static final Map<JoinPartyFinderOutcome, String> JOIN_KEYS = new EnumMap<>(JoinPartyFinderOutcome.class);

    static {
        GOAL_KEYS.put(PartyGoal.PARTY_GOAL_LEVELING, "partyfinder.goal.leveling");
        GOAL_KEYS.put(PartyGoal.PARTY_GOAL_QUEST, "partyfinder.goal.quest");
        GOAL_KEYS.put(PartyGoal.PARTY_GOAL_DUNGEON, "partyfinder.goal.dungeon");
        GOAL_KEYS.put(PartyGoal.PARTY_GOAL_WORLD_BOSS, "partyfinder.goal.world_boss");
        JOIN_KEYS.put(JoinPartyFinderOutcome.JOIN_PARTY_FINDER_OUTCOME_OK, "partyfinder.status.joined");
        JOIN_KEYS.put(JoinPartyFinderOutcome.JOIN_PARTY_FINDER_OUTCOME_NOT_FOUND, "partyfinder.error.not_found");
        JOIN_KEYS.put(
                JoinPartyFinderOutcome.JOIN_PARTY_FINDER_OUTCOME_LEVEL_TOO_LOW, "partyfinder.error.level_too_low");
        JOIN_KEYS.put(
                JoinPartyFinderOutcome.JOIN_PARTY_FINDER_OUTCOME_ALREADY_IN_PARTY,
                "partyfinder.error.already_in_party");
        JOIN_KEYS.put(JoinPartyFinderOutcome.JOIN_PARTY_FINDER_OUTCOME_PARTY_FULL, "partyfinder.error.party_full");
        JOIN_KEYS.put(
                JoinPartyFinderOutcome.JOIN_PARTY_FINDER_OUTCOME_INVITE_PENDING, "partyfinder.error.invite_pending");
        JOIN_KEYS.put(JoinPartyFinderOutcome.JOIN_PARTY_FINDER_OUTCOME_OWN_LISTING, "partyfinder.error.own_listing");
    }

    /** Việc của một dòng: đăng (mục tiêu), gỡ, hoặc vào nhóm của một trưởng nhóm. */
    private sealed interface Action {
        record Post(PartyGoal goal) implements Action {}

        record Remove() implements Action {}

        record Join(long leaderId) implements Action {}
    }

    private final ReportPanel panel;
    private final WorldCommandSender sender;
    private final long localCharacterId;
    private final I18NBundle i18n = KageI18n.load();
    private final List<Action> actions = new ArrayList<>();

    PartyFinderCoordinator(ReportPanel panel, WorldCommandSender sender, long localCharacterId) {
        this.panel = panel;
        this.sender = sender;
        this.localCharacterId = localCharacterId;
        panel.setCanOpen(() -> false);
        panel.setOnRefresh(this::request);
        panel.setOnRowAction(this::onRowAction);
        panel.setStatus(text("partyfinder.hint"));
    }

    private void request() {
        sender.sendReadRequest(
                Opcode.OPCODE_LIST_PARTY_FINDER_REQUEST_VALUE, ListPartyFinderRequest.getDefaultInstance());
    }

    private void onRowAction(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= actions.size()) {
            return;
        }
        switch (actions.get(rowIndex)) {
            case Action.Post post ->
                sender.sendReadRequest(
                        Opcode.OPCODE_POST_PARTY_FINDER_COMMAND_VALUE,
                        PostPartyFinderCommand.newBuilder()
                                .setGoal(post.goal())
                                .setMinLevel(1)
                                .build());
            case Action.Remove ignored ->
                sender.sendReadRequest(
                        Opcode.OPCODE_REMOVE_PARTY_FINDER_COMMAND_VALUE, RemovePartyFinderCommand.getDefaultInstance());
            case Action.Join join ->
                sender.sendReadRequest(
                        Opcode.OPCODE_JOIN_PARTY_FINDER_COMMAND_VALUE,
                        JoinPartyFinderCommand.newBuilder()
                                .setLeaderCharacterId(join.leaderId())
                                .build());
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
        if (opcode == Opcode.OPCODE_LIST_PARTY_FINDER_RESPONSE_VALUE) {
            show(ListPartyFinderResponse.parseFrom(frame.payload()), text("partyfinder.hint"));
            if (!panel.isOpen()) {
                panel.show();
            }
            return true;
        }
        if (opcode == Opcode.OPCODE_POST_PARTY_FINDER_RESPONSE_VALUE) {
            PostPartyFinderResponse response = PostPartyFinderResponse.parseFrom(frame.payload());
            String key =
                    switch (response.getOutcome()) {
                        case POST_PARTY_FINDER_OUTCOME_OK -> "partyfinder.status.posted";
                        case POST_PARTY_FINDER_OUTCOME_NOT_LEADER -> "partyfinder.error.not_leader";
                        default -> "partyfinder.error.unknown";
                    };
            show(response.getBoard(), text(key));
            return true;
        }
        if (opcode == Opcode.OPCODE_JOIN_PARTY_FINDER_RESPONSE_VALUE) {
            JoinPartyFinderResponse response = JoinPartyFinderResponse.parseFrom(frame.payload());
            show(response.getBoard(), text(JOIN_KEYS.getOrDefault(response.getOutcome(), "partyfinder.error.unknown")));
            return true;
        }
        if (opcode == Opcode.OPCODE_REMOVE_PARTY_FINDER_RESPONSE_VALUE) {
            show(RemovePartyFinderResponse.parseFrom(frame.payload()).getBoard(), text("partyfinder.status.removed"));
            return true;
        }
        return false;
    }

    private void show(ListPartyFinderResponse board, String status) {
        panel.setStatus(status);
        actions.clear();
        List<ReportPanel.Row> rows = new ArrayList<>();
        for (PartyGoal goal : GOALS) {
            rows.add(new ReportPanel.Row(
                    KageI18n.formatOrKey(i18n, "partyfinder.post_row", text(GOAL_KEYS.get(goal))),
                    false,
                    "partyfinder.action.post"));
            actions.add(new Action.Post(goal));
        }
        rows.add(new ReportPanel.Row(text("partyfinder.remove_row"), false, "partyfinder.action.remove"));
        actions.add(new Action.Remove());
        for (PartyFinderListingView listing : board.getListingsList()) {
            if (listing.getLeaderCharacterId() == localCharacterId) {
                continue; // tin của chính mình đã có dòng "gỡ"
            }
            rows.add(new ReportPanel.Row(
                    KageI18n.formatOrKey(
                            i18n,
                            "partyfinder.row",
                            listing.getLeaderName(),
                            text(GOAL_KEYS.getOrDefault(listing.getGoal(), "partyfinder.goal.leveling")),
                            listing.getMinLevel(),
                            Math.max(1L, listing.getExpiresInSeconds() / 60L)),
                    false,
                    "partyfinder.action.join"));
            actions.add(new Action.Join(listing.getLeaderCharacterId()));
        }
        panel.setRows(rows);
    }

    private String text(String key) {
        return KageI18n.textOrKey(i18n, key);
    }

    @Override
    public void reset() {
        panel.close();
        actions.clear();
        panel.setRows(List.of());
        panel.setStatus(text("partyfinder.hint"));
    }
}

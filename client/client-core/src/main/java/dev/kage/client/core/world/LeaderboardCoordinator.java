package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.CultivationRealm;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.Faction;
import dev.kage.protocol.GetLeaderboardRequest;
import dev.kage.protocol.GetLeaderboardResponse;
import dev.kage.protocol.LeaderboardEntry;
import dev.kage.protocol.LeaderboardKind;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Bia Anh Danh (ADR-057, phím L) — ba tab (cảnh giới / đấu trường / Lực Chiến), hỏi lại server mỗi lần
 * mở hoặc đổi tab. Dòng của chính mình tô màu VÀ có dấu "▶" — không dựa riêng vào màu (client/CLAUDE.md).
 */
final class LeaderboardCoordinator implements WindowFlow {

    /** Thứ tự tab trong {@link ReportPanel} — khớp danh sách khoá truyền vào constructor của panel. */
    static final List<String> TAB_KEYS =
            List.of("leaderboard.tab.realm", "leaderboard.tab.arena", "leaderboard.tab.combat_power");

    private static final List<LeaderboardKind> TAB_KINDS = List.of(
            LeaderboardKind.LEADERBOARD_KIND_REALM,
            LeaderboardKind.LEADERBOARD_KIND_ARENA,
            LeaderboardKind.LEADERBOARD_KIND_COMBAT_POWER);

    private static final String FACTION_PREFIX = "FACTION_";

    private final ReportPanel panel;
    private final WorldCommandSender sender;
    private final long localCharacterId;
    private final I18NBundle i18n = KageI18n.load();
    private LeaderboardKind selected = LeaderboardKind.LEADERBOARD_KIND_REALM;

    LeaderboardCoordinator(ReportPanel panel, WorldCommandSender sender, long localCharacterId) {
        this.panel = panel;
        this.sender = sender;
        this.localCharacterId = localCharacterId;
        panel.setOnRefresh(this::request);
        panel.setOnTabSelected(index -> {
            selected = TAB_KINDS.get(index);
            request();
        });
    }

    private void request() {
        panel.setStatus(text("leaderboard.status.loading"));
        sender.sendReadRequest(
                Opcode.OPCODE_GET_LEADERBOARD_REQUEST_VALUE,
                GetLeaderboardRequest.newBuilder().setKind(selected).build());
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
        if (frame.header().opcode() != Opcode.OPCODE_GET_LEADERBOARD_RESPONSE_VALUE) {
            return false;
        }
        GetLeaderboardResponse response = GetLeaderboardResponse.parseFrom(frame.payload());
        if (response.getKind() != selected) {
            return true; // trả lời của một tab đã bỏ — không vẽ đè tab đang xem
        }
        show(response);
        return true;
    }

    private void show(GetLeaderboardResponse response) {
        panel.setStatus(
                response.getOwnRank() > 0
                        ? format("leaderboard.status.own_rank", response.getOwnRank(), response.getOwnValue())
                        : text("leaderboard.status.unranked"));
        panel.setRows(rows(response));
    }

    List<ReportPanel.Row> rows(GetLeaderboardResponse response) {
        List<ReportPanel.Row> rows = new ArrayList<>();
        if (response.getEntriesCount() == 0) {
            rows.add(new ReportPanel.Row(text("leaderboard.empty"), false));
            return rows;
        }
        for (LeaderboardEntry entry : response.getEntriesList()) {
            boolean own = entry.getCharacterId() == localCharacterId;
            String line = format(
                    "leaderboard.row",
                    entry.getRank(),
                    entry.getName(),
                    factionLabel(entry.getFaction()),
                    realmLabel(entry.getLevel()),
                    valueLabel(response.getKind(), entry.getValue()));
            rows.add(new ReportPanel.Row(own ? "▶ " + line : line, own));
        }
        return rows;
    }

    private String valueLabel(LeaderboardKind kind, long value) {
        return switch (kind) {
            case LEADERBOARD_KIND_ARENA -> format("leaderboard.value.arena", value);
            case LEADERBOARD_KIND_COMBAT_POWER -> format("leaderboard.value.combat_power", value);
            default -> format("leaderboard.value.realm", value);
        };
    }

    private String factionLabel(Faction faction) {
        String name = faction.name();
        String suffix = name.startsWith(FACTION_PREFIX) ? name.substring(FACTION_PREFIX.length()) : name;
        return text("faction." + suffix.toLowerCase(Locale.ROOT));
    }

    private String realmLabel(int level) {
        CultivationRealm realm = CultivationRealm.of(level);
        String realmName = text(realm.nameKey());
        return realm.perfected()
                ? format("realm.perfect", realmName)
                : format("realm.tier_short", realmName, realm.tier());
    }

    private String text(String key) {
        return KageI18n.textOrKey(i18n, key);
    }

    private String format(String key, Object... args) {
        return KageI18n.formatOrKey(i18n, key, args);
    }

    @Override
    public void reset() {
        panel.close();
        panel.setRows(List.of());
        selected = LeaderboardKind.LEADERBOARD_KIND_REALM;
    }
}

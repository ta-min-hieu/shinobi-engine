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
import dev.kage.protocol.ClaimRealmMilestoneCommand;
import dev.kage.protocol.ClaimRealmMilestoneOutcome;
import dev.kage.protocol.ClaimRealmMilestoneResponse;
import dev.kage.protocol.GetRealmMilestonesRequest;
import dev.kage.protocol.GetRealmMilestonesResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RealmMilestoneView;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Quà mốc cảnh giới (ADR-153) — cửa sổ mở bằng lệnh chat {@code /mocgioi} (hoặc {@code /milestones}): mỗi mốc một
 * dòng; mốc đã đạt mà chưa nhận có nút "Nhận". Server kiểm cấp và chống nhận hai lần.
 */
final class MilestoneCoordinator implements WindowFlow {

    private static final List<String> COMMANDS = List.of("/mocgioi", "/milestones");
    private static final String OUTCOME_PREFIX = "CLAIM_REALM_MILESTONE_OUTCOME_";
    /** Bảng thật có 6 mốc — gói dị dạng không được kéo theo hàng nghìn dòng. */
    private static final int MAX_ROWS = 20;

    private final ReportPanel panel;
    private final WorldCommandSender sender;
    private final ItemCatalogCoordinator itemCatalog;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final List<Integer> rowLevels = new ArrayList<>();

    MilestoneCoordinator(ReportPanel panel, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        this.panel = panel;
        this.sender = sender;
        this.itemCatalog = feeds.itemCatalog();
        this.chat = feeds.chat();
        panel.setOnRefresh(this::request);
        panel.setOnRowAction(this::onRowAction);
        panel.setStatus(text("milestone.hint"));
        chat.addSlashCommands(this::onSlash);
    }

    static MilestoneCoordinator create(KageSkin skin, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new MilestoneCoordinator(
                new ReportPanel(skin, "milestone.title", Input.Keys.UNKNOWN, List.of()), sender, feeds);
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
                Opcode.OPCODE_GET_REALM_MILESTONES_REQUEST_VALUE, GetRealmMilestonesRequest.getDefaultInstance());
    }

    private void onRowAction(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= rowLevels.size()) {
            return;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_CLAIM_REALM_MILESTONE_COMMAND_VALUE,
                ClaimRealmMilestoneCommand.newBuilder()
                        .setLevel(rowLevels.get(rowIndex))
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
        if (opcode == Opcode.OPCODE_GET_REALM_MILESTONES_RESPONSE_VALUE) {
            show(GetRealmMilestonesResponse.parseFrom(frame.payload()));
            return true;
        }
        if (opcode != Opcode.OPCODE_CLAIM_REALM_MILESTONE_RESPONSE_VALUE) {
            return false;
        }
        ClaimRealmMilestoneResponse response = ClaimRealmMilestoneResponse.parseFrom(frame.payload());
        show(response.getBoard());
        ClaimRealmMilestoneOutcome outcome = response.getOutcome();
        if (outcome == ClaimRealmMilestoneOutcome.CLAIM_REALM_MILESTONE_OUTCOME_OK) {
            String line = KageI18n.formatOrKey(i18n, "milestone.claimed", response.getLevel());
            panel.setStatus(line);
            chat.showSystemNotice(line);
            sender.listInventory(); // quà vừa vào túi (linh thạch tới riêng qua GOLD_CHANGED_EVENT)
        } else {
            String suffix = outcome == ClaimRealmMilestoneOutcome.UNRECOGNIZED
                    ? "unspecified"
                    : outcome.name().substring(OUTCOME_PREFIX.length()).toLowerCase(Locale.ROOT);
            panel.setStatus(text("milestone.outcome." + suffix));
        }
        return true;
    }

    private void show(GetRealmMilestonesResponse board) {
        rowLevels.clear();
        List<ReportPanel.Row> rows = new ArrayList<>();
        for (RealmMilestoneView m : board.getMilestonesList()) {
            if (rows.size() >= MAX_ROWS) {
                break;
            }
            boolean reached = board.getCharacterLevel() >= m.getLevel();
            String state = m.getClaimed()
                    ? text("milestone.state.claimed")
                    : reached ? text("milestone.state.ready") : text("milestone.state.locked");
            String line =
                    KageI18n.formatOrKey(i18n, "milestone.row", m.getLevel(), text(m.getNameKey()), reward(m), state);
            boolean claimable = reached && !m.getClaimed();
            rows.add(new ReportPanel.Row(line, claimable, claimable ? "milestone.action.claim" : ""));
            rowLevels.add(m.getLevel());
        }
        panel.setRows(rows);
    }

    private String reward(RealmMilestoneView m) {
        StringBuilder reward = new StringBuilder(KageI18n.formatOrKey(i18n, "milestone.reward.gold", m.getGold()));
        if (m.getItemTemplateId() != 0) {
            String name = itemCatalog
                    .nameKey(m.getItemTemplateId())
                    .map(this::text)
                    .orElseGet(() -> "#" + m.getItemTemplateId());
            reward.append(" + ").append(name).append(" x").append(m.getQuantity());
        }
        return reward.toString();
    }

    private String text(String key) {
        return KageI18n.textOrKey(i18n, key);
    }

    @Override
    public void reset() {
        panel.close();
        rowLevels.clear();
        panel.setRows(List.of());
    }
}

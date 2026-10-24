package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GuildSkillView;
import dev.kage.protocol.ListGuildSkillsRequest;
import dev.kage.protocol.ListGuildSkillsResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.UpgradeGuildSkillCommand;
import dev.kage.protocol.UpgradeGuildSkillOutcome;
import dev.kage.protocol.UpgradeGuildSkillResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Kỹ năng bang (ADR-085) — {@link ReportPanel}: mỗi kỹ năng một dòng (bậc, hiệu lực mỗi bậc), nút "Nâng" chỉ hiện cho
 * bang chủ khi còn điểm. Mở từ nút trong cửa sổ Bang (G). Server vẫn kiểm lại mọi thứ.
 */
final class GuildSkillCoordinator implements WindowFlow {

    private static final Map<UpgradeGuildSkillOutcome, String> OUTCOME_KEYS =
            new EnumMap<>(UpgradeGuildSkillOutcome.class);

    static {
        OUTCOME_KEYS.put(UpgradeGuildSkillOutcome.UPGRADE_GUILD_SKILL_OUTCOME_OK, "guildskill.status.ok");
        OUTCOME_KEYS.put(
                UpgradeGuildSkillOutcome.UPGRADE_GUILD_SKILL_OUTCOME_NOT_IN_GUILD, "guildskill.error.not_in_guild");
        OUTCOME_KEYS.put(
                UpgradeGuildSkillOutcome.UPGRADE_GUILD_SKILL_OUTCOME_NOT_LEADER, "guildskill.error.not_leader");
        OUTCOME_KEYS.put(UpgradeGuildSkillOutcome.UPGRADE_GUILD_SKILL_OUTCOME_MAX_RANK, "guildskill.error.max_rank");
        OUTCOME_KEYS.put(UpgradeGuildSkillOutcome.UPGRADE_GUILD_SKILL_OUTCOME_NO_POINTS, "guildskill.error.no_points");
    }

    private final ReportPanel panel;
    private final WorldCommandSender sender;
    private final I18NBundle i18n = KageI18n.load();
    private final List<String> rowCodes = new ArrayList<>();

    GuildSkillCoordinator(ReportPanel panel, WorldCommandSender sender) {
        this.panel = panel;
        this.sender = sender;
        panel.setCanOpen(() -> false);
        panel.setOnRefresh(this::request);
        panel.setOnRowAction(this::onRowAction);
    }

    private void request() {
        sender.sendReadRequest(
                Opcode.OPCODE_LIST_GUILD_SKILLS_REQUEST_VALUE, ListGuildSkillsRequest.getDefaultInstance());
    }

    private void onRowAction(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= rowCodes.size()) {
            return;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_UPGRADE_GUILD_SKILL_COMMAND_VALUE,
                UpgradeGuildSkillCommand.newBuilder()
                        .setCode(rowCodes.get(rowIndex))
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
        if (opcode == Opcode.OPCODE_LIST_GUILD_SKILLS_RESPONSE_VALUE) {
            show(ListGuildSkillsResponse.parseFrom(frame.payload()), "");
            if (!panel.isOpen()) {
                panel.show();
            }
            return true;
        }
        if (opcode == Opcode.OPCODE_UPGRADE_GUILD_SKILL_RESPONSE_VALUE) {
            UpgradeGuildSkillResponse response = UpgradeGuildSkillResponse.parseFrom(frame.payload());
            show(
                    response.getSheet(),
                    text(OUTCOME_KEYS.getOrDefault(response.getOutcome(), "guildskill.error.unknown")));
            return true;
        }
        return false;
    }

    private void show(ListGuildSkillsResponse sheet, String notice) {
        String points = sheet.getInGuild()
                ? KageI18n.formatOrKey(i18n, "guildskill.points", sheet.getPointsAvailable(), sheet.getPointsTotal())
                : text("guildskill.error.not_in_guild");
        panel.setStatus(notice.isEmpty() ? points : notice + " " + points);
        rowCodes.clear();
        List<ReportPanel.Row> rows = new ArrayList<>();
        boolean canUpgrade = sheet.getLeader() && sheet.getPointsAvailable() > 0;
        for (GuildSkillView skill : sheet.getSkillsList()) {
            String line = KageI18n.formatOrKey(
                    i18n,
                    "guildskill.row",
                    text("guildskill.name." + skill.getCode()),
                    skill.getRank(),
                    skill.getMaxRank(),
                    KageI18n.formatOrKey(i18n, "guildskill.desc." + skill.getCode(), skill.getPerRank()));
            boolean upgradable = canUpgrade && skill.getRank() < skill.getMaxRank();
            rows.add(new ReportPanel.Row(line, false, upgradable ? "guildskill.action.upgrade" : ""));
            rowCodes.add(skill.getCode());
        }
        panel.setRows(rows);
    }

    private String text(String key) {
        return KageI18n.textOrKey(i18n, key);
    }

    @Override
    public void reset() {
        panel.close();
        rowCodes.clear();
        panel.setRows(List.of());
    }
}

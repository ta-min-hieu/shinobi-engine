package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ListSkillRanksRequest;
import dev.kage.protocol.ListSkillRanksResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SkillRankView;
import dev.kage.protocol.UpgradeSkillCommand;
import dev.kage.protocol.UpgradeSkillOutcome;
import dev.kage.protocol.UpgradeSkillResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Cửa sổ Công pháp (ADR-071, phím K) — bậc từng chiêu của linh căn hiện tại và điểm còn. Client không tính điểm:
 * nút "Nâng" chỉ bày khi server nói còn điểm và chiêu chưa tối đa; server vẫn kiểm lại.
 */
final class SkillRankCoordinator implements WindowFlow {

    private static final Map<UpgradeSkillOutcome, String> OUTCOME_KEYS = Map.of(
            UpgradeSkillOutcome.UPGRADE_SKILL_OUTCOME_OK, "skillrank.notice.upgraded",
            UpgradeSkillOutcome.UPGRADE_SKILL_OUTCOME_UNKNOWN_SKILL, "skillrank.error.unknown_skill",
            UpgradeSkillOutcome.UPGRADE_SKILL_OUTCOME_LEVEL_TOO_LOW, "skillrank.error.level_too_low",
            UpgradeSkillOutcome.UPGRADE_SKILL_OUTCOME_MAX_RANK, "skillrank.error.max_rank",
            UpgradeSkillOutcome.UPGRADE_SKILL_OUTCOME_NO_POINTS, "skillrank.error.no_points");

    /** Mã chiêu của từng dòng, cùng chỉ số với {@link ReportPanel#setRows}. */
    private final List<String> rowCodes = new ArrayList<>();

    private final ReportPanel panel;
    private final WorldCommandSender sender;
    private final I18NBundle i18n = KageI18n.load();

    SkillRankCoordinator(ReportPanel panel, WorldCommandSender sender) {
        this.panel = panel;
        this.sender = sender;
        panel.setOnRefresh(this::request);
        panel.setOnRowAction(this::onRowAction);
        panel.setStatus(text("skillrank.hint.open"));
    }

    private void request() {
        panel.setStatus(text("skillrank.status.loading"));
        sender.sendReadRequest(
                Opcode.OPCODE_LIST_SKILL_RANKS_REQUEST_VALUE, ListSkillRanksRequest.getDefaultInstance());
    }

    private void onRowAction(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= rowCodes.size()) {
            return;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_UPGRADE_SKILL_COMMAND_VALUE,
                UpgradeSkillCommand.newBuilder()
                        .setSkillCode(rowCodes.get(rowIndex))
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
        if (opcode == Opcode.OPCODE_LIST_SKILL_RANKS_RESPONSE_VALUE) {
            show(ListSkillRanksResponse.parseFrom(frame.payload()), "");
            return true;
        }
        if (opcode == Opcode.OPCODE_UPGRADE_SKILL_RESPONSE_VALUE) {
            UpgradeSkillResponse response = UpgradeSkillResponse.parseFrom(frame.payload());
            show(
                    response.getSheet(),
                    text(OUTCOME_KEYS.getOrDefault(response.getOutcome(), "skillrank.error.unknown")));
            return true;
        }
        return false;
    }

    private void show(ListSkillRanksResponse sheet, String notice) {
        String points = format("skillrank.points", sheet.getPointsAvailable(), sheet.getPointsTotal());
        panel.setStatus(notice.isEmpty() ? points : notice + " " + points);
        rowCodes.clear();
        List<ReportPanel.Row> rows = new ArrayList<>();
        boolean hasPoints = sheet.getPointsAvailable() > 0;
        for (SkillRankView skill : sheet.getSkillsList()) {
            String line = format(
                    "skillrank.row", text("skill.name." + skill.getSkillCode()), skill.getRank(), skill.getMaxRank());
            boolean upgradable = hasPoints && skill.getRank() < skill.getMaxRank() && skill.getUnlocked();
            rows.add(new ReportPanel.Row(
                    skill.getUnlocked() ? line : line + " " + format("skillrank.locked", skill.getRequiredLevel()),
                    false,
                    upgradable ? "skillrank.action.upgrade" : ""));
            rowCodes.add(skill.getSkillCode());
        }
        panel.setRows(rows);
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
        rowCodes.clear();
        panel.setStatus(text("skillrank.hint.open"));
    }
}

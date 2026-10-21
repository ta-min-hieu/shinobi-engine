package dev.kage.tools.loadtest.probe;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

import dev.kage.protocol.DonateToGuildCommand;
import dev.kage.protocol.DonateToGuildResponse;
import dev.kage.protocol.Faction;
import dev.kage.protocol.GuildProgressView;
import dev.kage.protocol.ListSkillRanksRequest;
import dev.kage.protocol.ListSkillRanksResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SelectFactionRequest;
import dev.kage.protocol.SelectFactionResponse;
import dev.kage.protocol.SkillRankView;
import dev.kage.protocol.UpgradeSkillCommand;
import dev.kage.protocol.UpgradeSkillResponse;
import dev.kage.tools.loadtest.GameConnection;

/**
 * Ba bước "tiến triển nhân vật" của lô 17 mà trước đó chỉ client có giao diện mới gửi được — đổi
 * linh căn (ADR-066, 206/207), nâng bậc chiêu (ADR-071, 409–412), góp vàng cho bang (ADR-068,
 * 721/722). Không có kịch bản này thì ba mục đó chỉ kiểm được bằng tay qua client.
 *
 * <p>Thứ tự trong một lượt: đổi linh căn TRƯỚC — chiêu được nâng là chiêu của linh căn hiện tại, nên
 * nâng trước rồi đổi thì bảng bậc in ra là của hệ khác. In bảng bậc chiêu TRƯỚC và SAU khi nâng để
 * thấy điểm trừ đúng.
 */
final class ProgressionScenario {

    /** Ngân sách bỏ qua AoI event khi chờ 1 phản hồi cụ thể — cùng lý do đã ghi ở {@code QuestScenario}. */
    private static final int MAX_FRAMES_TO_SKIP = 2000;

    private final GameConnection connection;
    private final ProbeSession session;

    ProgressionScenario(GameConnection connection, ProbeSession session) {
        this.connection = connection;
        this.session = session;
    }

    void run(ProbeConfig.ProgressionPlan plan) throws IOException {
        if (!plan.selectFaction().isEmpty()) {
            selectFaction(plan.selectFaction());
        }
        if (!plan.upgradeSkills().isEmpty()) {
            printSheet("trước khi nâng", listSkillRanks());
            for (String skillCode : plan.upgradeSkills()) {
                upgradeSkill(skillCode);
            }
        }
        if (plan.donateToGuild() > 0) {
            donateToGuild(plan.donateToGuild());
        }
    }

    private void selectFaction(String rawFaction) throws IOException {
        Faction faction = Faction.valueOf("FACTION_" + rawFaction.toUpperCase(Locale.ROOT));
        connection.send(
                Opcode.OPCODE_SELECT_FACTION_REQUEST_VALUE,
                SelectFactionRequest.newBuilder().setFaction(faction).build().toByteArray());
        // Server báo MỌI thất bại đổi hệ qua ErrorResponse (opcode 5), không qua error_key của 207 —
        // xem FactionCoordinator phía client. expectFrame dừng ở frame lỗi thay vì chờ 207 tới hết giờ.
        SelectFactionResponse response;
        try {
            response = SelectFactionResponse.parseFrom(connection
                    .expectFrame(Opcode.OPCODE_SELECT_FACTION_RESPONSE_VALUE, "SelectFactionResponse")
                    .payload());
        } catch (IOException rejected) {
            session.note("đổi linh căn sang " + faction + ": " + rejected.getMessage());
            return;
        }
        session.note(String.format(
                Locale.ROOT,
                "đổi linh căn sang %s: success=%s errorKey='%s'",
                faction,
                response.getSuccess(),
                response.getErrorKey()));
    }

    private ListSkillRanksResponse listSkillRanks() throws IOException {
        connection.send(
                Opcode.OPCODE_LIST_SKILL_RANKS_REQUEST_VALUE,
                ListSkillRanksRequest.getDefaultInstance().toByteArray());
        return ListSkillRanksResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_LIST_SKILL_RANKS_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
    }

    private void upgradeSkill(String skillCode) throws IOException {
        connection.send(
                Opcode.OPCODE_UPGRADE_SKILL_COMMAND_VALUE,
                UpgradeSkillCommand.newBuilder().setSkillCode(skillCode).build().toByteArray());
        UpgradeSkillResponse response = UpgradeSkillResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_UPGRADE_SKILL_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        session.note("nâng chiêu " + skillCode + ": " + response.getOutcome());
        printSheet("sau khi nâng " + skillCode, response.getSheet());
    }

    private void printSheet(String label, ListSkillRanksResponse sheet) {
        StringBuilder line = new StringBuilder(String.format(
                Locale.ROOT, "bậc chiêu %s: điểm=%d/%d", label, sheet.getPointsAvailable(), sheet.getPointsTotal()));
        for (SkillRankView skill : sheet.getSkillsList()) {
            line.append(String.format(
                    Locale.ROOT,
                    " | %s %d/%d (Lv%d%s)",
                    skill.getSkillCode(),
                    skill.getRank(),
                    skill.getMaxRank(),
                    skill.getRequiredLevel(),
                    skill.getUnlocked() ? "" : ", khoá"));
        }
        session.note(line.toString());
    }

    private void donateToGuild(long amount) throws IOException {
        connection.send(
                Opcode.OPCODE_DONATE_TO_GUILD_COMMAND_VALUE,
                DonateToGuildCommand.newBuilder()
                        .setAmount(amount)
                        .setIdempotencyKey(UUID.randomUUID().toString())
                        .build()
                        .toByteArray());
        DonateToGuildResponse response = DonateToGuildResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_DONATE_TO_GUILD_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        GuildProgressView progress = response.getProgress();
        session.note(String.format(
                Locale.ROOT,
                "góp %d vàng cho bang: %s | cấp=%d exp=%d/%d thànhViênTốiĐa=%d cốngHiến=%d tuần=%d/%d%s",
                amount,
                response.getOutcome(),
                progress.getLevel(),
                progress.getExp(),
                progress.getExpForNextLevel(),
                progress.getMaxMembers(),
                progress.getMyContribution(),
                progress.getWeeklyKills(),
                progress.getWeeklyTarget(),
                progress.getWeeklyCompleted() ? " (xong)" : ""));
    }
}

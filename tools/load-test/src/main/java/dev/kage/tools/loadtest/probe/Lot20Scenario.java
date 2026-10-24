package dev.kage.tools.loadtest.probe;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import dev.kage.protocol.GetSpiritVeinStatusRequest;
import dev.kage.protocol.GetSpiritVeinStatusResponse;
import dev.kage.protocol.GuildSkillView;
import dev.kage.protocol.ListGuildSkillsRequest;
import dev.kage.protocol.ListGuildSkillsResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RebirthCommand;
import dev.kage.protocol.RebirthResponse;
import dev.kage.protocol.SendFriendGiftCommand;
import dev.kage.protocol.SendFriendGiftResponse;
import dev.kage.protocol.SpiritVeinScoreView;
import dev.kage.protocol.SpiritVeinView;
import dev.kage.protocol.UpgradeGuildSkillCommand;
import dev.kage.protocol.UpgradeGuildSkillResponse;
import dev.kage.tools.loadtest.GameConnection;

/**
 * Các tính năng lô 20 chỉ client có giao diện mới gửi được — tặng quà bạn bè (ADR-084, 1116/1117), kỹ năng bang
 * (ADR-085, 727–730), Độ Kiếp Chuyển Thế (ADR-089, 212/213), Linh Mạch (ADR-090, 731/732). Mỗi bước một chuỗi:
 * {@code friendGift:<tên>}, {@code guildSkills}, {@code upgradeGuildSkill:<mã>}, {@code rebirth}, {@code spiritVein}.
 */
final class Lot20Scenario {

    private static final int MAX_FRAMES_TO_SKIP = 2000;

    private final GameConnection connection;
    private final ProbeSession session;

    Lot20Scenario(GameConnection connection, ProbeSession session) {
        this.connection = connection;
        this.session = session;
    }

    void run(List<String> steps) throws IOException {
        for (String raw : steps) {
            String step = raw.strip();
            int colon = step.indexOf(':');
            String verb = colon < 0 ? step : step.substring(0, colon);
            String argument = colon < 0 ? "" : step.substring(colon + 1);
            switch (verb) {
                case "friendGift" -> friendGift(argument);
                case "guildSkills" -> printGuildSkills("kỹ năng bang", listGuildSkills());
                case "upgradeGuildSkill" -> upgradeGuildSkill(argument);
                case "rebirth" -> rebirth();
                case "spiritVein" -> spiritVein();
                default -> session.note("bước lô 20 không hiểu: " + step);
            }
        }
    }

    private void friendGift(String targetName) throws IOException {
        connection.send(
                Opcode.OPCODE_SEND_FRIEND_GIFT_COMMAND_VALUE,
                SendFriendGiftCommand.newBuilder()
                        .setTargetName(targetName)
                        .build()
                        .toByteArray());
        SendFriendGiftResponse response = SendFriendGiftResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_SEND_FRIEND_GIFT_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        session.note(String.format(
                Locale.ROOT,
                "tặng quà %s: %s thânMật=%d mốc=%d",
                targetName,
                response.getOutcome(),
                response.getIntimacy(),
                response.getMilestone()));
    }

    private ListGuildSkillsResponse listGuildSkills() throws IOException {
        connection.send(
                Opcode.OPCODE_LIST_GUILD_SKILLS_REQUEST_VALUE,
                ListGuildSkillsRequest.getDefaultInstance().toByteArray());
        return ListGuildSkillsResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_LIST_GUILD_SKILLS_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
    }

    private void printGuildSkills(String label, ListGuildSkillsResponse sheet) {
        StringBuilder line = new StringBuilder(String.format(
                Locale.ROOT,
                "%s: trongBang=%s bangChủ=%s điểm=%d/%d",
                label,
                sheet.getInGuild(),
                sheet.getLeader(),
                sheet.getPointsAvailable(),
                sheet.getPointsTotal()));
        for (GuildSkillView skill : sheet.getSkillsList()) {
            line.append(String.format(
                    Locale.ROOT,
                    " | %s bậc %d/%d (+%d/bậc)",
                    skill.getCode(),
                    skill.getRank(),
                    skill.getMaxRank(),
                    skill.getPerRank()));
        }
        session.note(line.toString());
    }

    private void upgradeGuildSkill(String code) throws IOException {
        connection.send(
                Opcode.OPCODE_UPGRADE_GUILD_SKILL_COMMAND_VALUE,
                UpgradeGuildSkillCommand.newBuilder().setCode(code).build().toByteArray());
        UpgradeGuildSkillResponse response = UpgradeGuildSkillResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_UPGRADE_GUILD_SKILL_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        printGuildSkills("nâng " + code + " → " + response.getOutcome(), response.getSheet());
    }

    private void rebirth() throws IOException {
        connection.send(
                Opcode.OPCODE_REBIRTH_COMMAND_VALUE,
                RebirthCommand.getDefaultInstance().toByteArray());
        RebirthResponse response = RebirthResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_REBIRTH_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        session.note("độ kiếp: " + response.getOutcome() + " sốLần=" + response.getRebirthCount());
    }

    private void spiritVein() throws IOException {
        connection.send(
                Opcode.OPCODE_GET_SPIRIT_VEIN_STATUS_REQUEST_VALUE,
                GetSpiritVeinStatusRequest.getDefaultInstance().toByteArray());
        GetSpiritVeinStatusResponse status = GetSpiritVeinStatusResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_GET_SPIRIT_VEIN_STATUS_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        StringBuilder line = new StringBuilder("linh mạch:");
        for (SpiritVeinView vein : status.getVeinsList()) {
            line.append(String.format(
                    Locale.ROOT,
                    " | %s X%.0f..%.0f mở=%s đổiSau=%ds chủ=%s",
                    vein.getCode(),
                    vein.getMinX(),
                    vein.getMaxX(),
                    vein.getActive(),
                    vein.getSecondsUntilChange(),
                    vein.getHolderGuildName().isEmpty() ? "-" : vein.getHolderGuildName()));
            for (SpiritVeinScoreView score : vein.getScoresList()) {
                line.append(String.format(Locale.ROOT, " [%s %d điểm]", score.getGuildName(), score.getPoints()));
            }
        }
        session.note(line.toString());
    }
}

package dev.kage.tools.loadtest.probe;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

import dev.kage.protocol.AbandonQuestCommand;
import dev.kage.protocol.AbandonQuestResponse;
import dev.kage.protocol.ClaimMailCommand;
import dev.kage.protocol.ClaimMailResponse;
import dev.kage.protocol.GetCharacterStatsRequest;
import dev.kage.protocol.GetCharacterStatsResponse;
import dev.kage.protocol.GetLeaderboardRequest;
import dev.kage.protocol.GetLeaderboardResponse;
import dev.kage.protocol.ItemSetProgressView;
import dev.kage.protocol.LeaderboardEntry;
import dev.kage.protocol.LeaderboardKind;
import dev.kage.protocol.ListMailRequest;
import dev.kage.protocol.ListMailResponse;
import dev.kage.protocol.MailView;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SendMailCommand;
import dev.kage.protocol.SendMailResponse;
import dev.kage.tools.loadtest.GameConnection;

/**
 * Bốn đường của lô 15 mà trước đó chỉ client có giao diện mới gửi được — bảng nhân vật + Lực Chiến
 * (ADR-056, 210/211), Bia Anh Danh (ADR-057, 1202/1203), bỏ nhiệm vụ (ADR-054, 612/613), lĩnh thư
 * (902–905, khoá idempotency ADR-025).
 *
 * <p>Lĩnh thư gửi MỖI khoá hai lần: lần hai phải ra {@code DUPLICATE_REQUEST} chứ không cộng vàng lần
 * nữa — thứ duy nhất phân biệt "retry an toàn" với "nhân đôi tiền" mà không cần đọc DB.
 */
final class InspectionScenario {

    /** Ngân sách bỏ qua AoI event khi chờ 1 phản hồi cụ thể — cùng lý do đã ghi ở {@code QuestScenario}. */
    private static final int MAX_FRAMES_TO_SKIP = 2000;

    /** Số dòng đầu bảng xếp hạng in ra — đủ thấy thứ tự mà không làm ngập báo cáo. */
    private static final int LEADERBOARD_ROWS_SHOWN = 5;

    private final GameConnection connection;
    private final ProbeSession session;

    InspectionScenario(GameConnection connection, ProbeSession session) {
        this.connection = connection;
        this.session = session;
    }

    void run(ProbeConfig.InspectionPlan plan) throws IOException {
        if (!plan.abandonQuest().isEmpty()) {
            abandon(plan.abandonQuest());
        }
        if (plan.claimAllMail()) {
            claimAllMail();
        }
        if (!plan.sendMail().isEmpty()) {
            sendMailTwice(plan.sendMail());
        }
        new FriendScenario(connection, session).run(plan.friendSteps());
        if (plan.statSheet()) {
            statSheet();
        }
        for (String kind : plan.leaderboards()) {
            leaderboard(LeaderboardKind.valueOf("LEADERBOARD_KIND_" + kind.toUpperCase(Locale.ROOT)));
        }
    }

    private void abandon(String questCode) throws IOException {
        connection.send(
                Opcode.OPCODE_ABANDON_QUEST_COMMAND_VALUE,
                AbandonQuestCommand.newBuilder().setQuestCode(questCode).build().toByteArray());
        AbandonQuestResponse response = AbandonQuestResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_ABANDON_QUEST_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        session.note("bỏ nhiệm vụ " + questCode + ": " + response.getOutcome());
    }

    private void claimAllMail() throws IOException {
        connection.send(
                Opcode.OPCODE_LIST_MAIL_REQUEST_VALUE,
                ListMailRequest.getDefaultInstance().toByteArray());
        ListMailResponse mails = ListMailResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_LIST_MAIL_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        session.note("hộp thư: " + mails.getMailsCount() + " thư");
        for (MailView mail : mails.getMailsList()) {
            if (mail.getClaimed()) {
                continue;
            }
            String key = UUID.randomUUID().toString();
            session.note(String.format(
                    Locale.ROOT,
                    "lĩnh thư %d '%s' (%d linh thạch, %d đính kèm): lần 1 %s, lần 2 cùng khoá %s",
                    mail.getMailId(),
                    mail.getSubject(),
                    mail.getGoldAmount(),
                    mail.getAttachmentsCount(),
                    claim(mail.getMailId(), key),
                    claim(mail.getMailId(), key)));
        }
    }

    /** ADR-058: cùng khoá gửi hai lần ⇒ lần hai phải là DUPLICATE_REQUEST, vàng trừ một lần. */
    private void sendMailTwice(String recipientAndGold) throws IOException {
        String[] parts = recipientAndGold.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("--sendMail cần dạng nguoiNhan:soVang, nhận '" + recipientAndGold + "'");
        }
        SendMailCommand command = SendMailCommand.newBuilder()
                .setRecipientName(parts[0])
                .setSubject("probe")
                .setBody("probe ADR-058")
                .setGoldAmount(Long.parseLong(parts[1]))
                .setIdempotencyKey(UUID.randomUUID().toString())
                .build();
        session.note("gửi thư cho " + parts[0] + " kèm " + parts[1] + " linh thạch: lần 1 " + send(command)
                + ", lần 2 cùng khoá " + send(command));
    }

    private String send(SendMailCommand command) throws IOException {
        connection.send(Opcode.OPCODE_SEND_MAIL_COMMAND_VALUE, command.toByteArray());
        SendMailResponse response = SendMailResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_SEND_MAIL_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        return response.getOutcome() + " (thư " + response.getMailId() + ")";
    }

    private String claim(long mailId, String idempotencyKey) throws IOException {
        connection.send(
                Opcode.OPCODE_CLAIM_MAIL_COMMAND_VALUE,
                ClaimMailCommand.newBuilder()
                        .setMailId(mailId)
                        .setIdempotencyKey(idempotencyKey)
                        .build()
                        .toByteArray());
        return ClaimMailResponse.parseFrom(connection
                        .readUntilOpcode(Opcode.OPCODE_CLAIM_MAIL_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                        .payload())
                .getOutcome()
                .name();
    }

    private void statSheet() throws IOException {
        connection.send(
                Opcode.OPCODE_GET_CHARACTER_STATS_REQUEST_VALUE,
                GetCharacterStatsRequest.getDefaultInstance().toByteArray());
        GetCharacterStatsResponse sheet = GetCharacterStatsResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_GET_CHARACTER_STATS_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        StringBuilder line = new StringBuilder(String.format(
                Locale.ROOT,
                "bảng nhân vật: cấp=%d máu=%d (trangBị +%d) chân khí=%d công=%d phépCông=%d thủ=%d kháng=%d"
                        + " chínhXác=%d‰ néTránh=%d‰ chíMạng=%d‰ (×%d%%) tốcĐộ+%d%% LựcChiến=%d",
                sheet.getLevel(),
                sheet.getMaxHp(),
                sheet.getEquipmentBonusHp(),
                sheet.getMaxChi(),
                sheet.getAttack(),
                sheet.getSkillDamage(),
                sheet.getDefense(),
                sheet.getResistance(),
                sheet.getHitPermille(),
                sheet.getDodgePermille(),
                sheet.getCritPermille(),
                sheet.getCritDamagePercent(),
                sheet.getMoveSpeedPercent(),
                sheet.getCombatPower()));
        for (ItemSetProgressView set : sheet.getItemSetsList()) {
            line.append(" | bộ ").append(set).append(' ');
        }
        if (sheet.hasPet()) {
            line.append(" | linh thú ").append(sheet.getPet().toString().replace('\n', ' '));
        }
        session.note(line.toString().replace('\n', ' '));
    }

    private void leaderboard(LeaderboardKind kind) throws IOException {
        connection.send(
                Opcode.OPCODE_GET_LEADERBOARD_REQUEST_VALUE,
                GetLeaderboardRequest.newBuilder().setKind(kind).build().toByteArray());
        GetLeaderboardResponse board = GetLeaderboardResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_GET_LEADERBOARD_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        StringBuilder line = new StringBuilder(String.format(
                Locale.ROOT,
                "bảng %s: %d dòng, hạng mình=%d giáTrị=%d",
                kind,
                board.getEntriesCount(),
                board.getOwnRank(),
                board.getOwnValue()));
        board.getEntriesList().stream()
                .limit(LEADERBOARD_ROWS_SHOWN)
                .forEach((LeaderboardEntry entry) -> line.append(String.format(
                        Locale.ROOT,
                        " | #%d %s Lv%d %d",
                        entry.getRank(),
                        entry.getName(),
                        entry.getLevel(),
                        entry.getValue())));
        session.note(line.toString());
    }
}

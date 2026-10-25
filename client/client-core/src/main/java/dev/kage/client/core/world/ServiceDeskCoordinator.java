package dev.kage.client.core.world;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RedeemGiftCodeCommand;
import dev.kage.protocol.RedeemGiftCodeResponse;
import dev.kage.protocol.ReportPlayerCommand;
import dev.kage.protocol.ReportPlayerResponse;
import dev.kage.protocol.ReportReason;
import dev.kage.protocol.ServerNoticeEvent;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Lô 22 phần 1 phía client — cửa sổ thông báo server (ADR-103), lệnh chat {@code /giftcode <MÃ>} (ADR-104) và
 * {@code /report <tên> <chat|cheat|scam|other> [ghi chú]} (ADR-105). Lệnh chat không lên kênh chat nào: chúng thành
 * lệnh riêng, kết quả về thành một dòng hệ thống. Server quyết hết.
 */
final class ServiceDeskCoordinator implements WindowFlow {

    private static final String GIFT_CODE = "/giftcode ";
    private static final String REPORT = "/report ";
    private static final int REPORT_MIN_WORDS = 2;

    private final ReportPanel panel;
    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    ServiceDeskCoordinator(ReportPanel panel, WorldCommandSender sender, ChatCoordinator chat) {
        this.panel = panel;
        this.sender = sender;
        this.chat = chat;
        panel.setCanOpen(() -> false);
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        if (lower.startsWith(GIFT_CODE)) {
            String code = line.substring(GIFT_CODE.length()).strip();
            if (code.isEmpty()) {
                chat.showSystemNotice(text("giftcode.usage"));
            } else {
                sender.sendReadRequest(
                        Opcode.OPCODE_REDEEM_GIFT_CODE_COMMAND_VALUE,
                        RedeemGiftCodeCommand.newBuilder().setCode(code).build());
            }
            return true;
        }
        if (lower.startsWith(REPORT)) {
            report(line.substring(REPORT.length()).strip());
            return true;
        }
        return false;
    }

    private void report(String rest) {
        String[] words = rest.split("\\s+", 3);
        Optional<ReportReason> reason = words.length >= REPORT_MIN_WORDS ? reasonOf(words[1]) : Optional.empty();
        if (reason.isEmpty()) {
            chat.showSystemNotice(text("report.usage"));
            return;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_REPORT_PLAYER_COMMAND_VALUE,
                ReportPlayerCommand.newBuilder()
                        .setTargetName(words[0])
                        .setReason(reason.get())
                        .setNote(words.length > REPORT_MIN_WORDS ? words[2] : "")
                        .build());
    }

    private static Optional<ReportReason> reasonOf(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "chat" -> Optional.of(ReportReason.REPORT_REASON_CHAT);
            case "cheat", "hack" -> Optional.of(ReportReason.REPORT_REASON_CHEAT);
            case "scam" -> Optional.of(ReportReason.REPORT_REASON_SCAM);
            case "other" -> Optional.of(ReportReason.REPORT_REASON_OTHER);
            default -> Optional.empty();
        };
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
        if (opcode == Opcode.OPCODE_SERVER_NOTICE_EVENT_VALUE) {
            String body = ServerNoticeEvent.parseFrom(frame.payload()).getBody();
            chat.showSystemNotice(KageI18n.formatOrKey(i18n, "notice.line", body));
            panel.setStatus(body);
            panel.setRows(List.of());
            if (!panel.isOpen()) {
                panel.show();
            }
            return true;
        }
        if (opcode == Opcode.OPCODE_REDEEM_GIFT_CODE_RESPONSE_VALUE) {
            chat.showSystemNotice(text("giftcode."
                    + suffix(RedeemGiftCodeResponse.parseFrom(frame.payload())
                            .getOutcome()
                            .name())));
            return true;
        }
        if (opcode == Opcode.OPCODE_REPORT_PLAYER_RESPONSE_VALUE) {
            chat.showSystemNotice(text("report."
                    + suffix(ReportPlayerResponse.parseFrom(frame.payload())
                            .getOutcome()
                            .name())));
            return true;
        }
        return false;
    }

    /** {@code REDEEM_GIFT_CODE_OUTCOME_OK} → {@code ok}. */
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
    }
}

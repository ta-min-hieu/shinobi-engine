package dev.kage.client.core.world;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.LongSupplier;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ArchiveMailCommand;
import dev.kage.protocol.ArchiveMailOutcome;
import dev.kage.protocol.ArchiveMailResponse;
import dev.kage.protocol.ArchivedMail;
import dev.kage.protocol.ListArchivedMailRequest;
import dev.kage.protocol.ListArchivedMailResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Lưu trữ thư (ADR-354) — {@code /luuthu} cất thư đang chọn trong hộp thư, {@code /thuluutru} xem kho lưu trữ, {@code
 * /traluu <số thư>} trả thư về hộp chính (hoặc {@code /archive}, {@code /archived}, {@code /unarchive}). Cất/trả xong
 * thì hỏi lại hộp thư.
 */
final class MailArchiveCoordinator implements WindowFlow {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private LongSupplier selectedMail = () -> 0L;

    MailArchiveCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    /** Nối thư đang chọn của hộp thư (0 = chưa chọn). */
    void useSelection(LongSupplier selected) {
        this.selectedMail = selected;
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        switch (command) {
            case "/luuthu", "/archive" -> archiveSelected();
            case "/thuluutru", "/archived" ->
                sender.sendReadRequest(
                        Opcode.OPCODE_LIST_ARCHIVED_MAIL_REQUEST_VALUE, ListArchivedMailRequest.getDefaultInstance());
            case "/traluu", "/unarchive" -> restore(words.length > 1 ? words[1].strip() : "");
            default -> {
                return false;
            }
        }
        return true;
    }

    private void archiveSelected() {
        long mail = selectedMail.getAsLong();
        if (mail == 0L) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "archive.select_first"));
            return;
        }
        send(mail, true);
    }

    private void restore(String arg) {
        try {
            send(Long.parseLong(arg), false);
        } catch (NumberFormatException e) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "archive.usage")); // không phải số thư
        }
    }

    private void send(long mailId, boolean archive) {
        sender.sendReadRequest(
                Opcode.OPCODE_ARCHIVE_MAIL_COMMAND_VALUE,
                ArchiveMailCommand.newBuilder()
                        .setMailId(mailId)
                        .setArchive(archive)
                        .build());
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_ARCHIVE_MAIL_RESPONSE_VALUE) {
            ArchiveMailResponse response = ArchiveMailResponse.parseFrom(frame.payload());
            String key = "archive."
                    + response.getOutcome()
                            .name()
                            .replace("ARCHIVE_MAIL_OUTCOME_", "")
                            .toLowerCase(Locale.ROOT);
            chat.showSystemNotice(KageI18n.formatOrKey(i18n, key, response.getMailId()));
            if (response.getOutcome() != ArchiveMailOutcome.ARCHIVE_MAIL_OUTCOME_NOT_FOUND) {
                sender.listMail(); // hộp chính đổi
            }
            return true;
        }
        if (opcode != Opcode.OPCODE_LIST_ARCHIVED_MAIL_RESPONSE_VALUE) {
            return false;
        }
        ListArchivedMailResponse response = ListArchivedMailResponse.parseFrom(frame.payload());
        chat.showSystemNotice(
                KageI18n.textOrKey(i18n, response.getMailsCount() == 0 ? "archive.empty" : "archive.header"));
        ZoneId zone = ZoneId.systemDefault();
        for (ArchivedMail mail : response.getMailsList()) {
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n,
                    "archive.line",
                    mail.getMailId(),
                    DATE.format(
                            Instant.ofEpochSecond(mail.getSentAtEpochSeconds()).atZone(zone)),
                    mail.getSenderName().isEmpty() ? KageI18n.textOrKey(i18n, "archive.system") : mail.getSenderName(),
                    ChatController.sanitize(mail.getSubject())));
        }
        return true;
    }
}

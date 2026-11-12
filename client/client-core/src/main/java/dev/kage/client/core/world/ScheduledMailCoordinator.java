package dev.kage.client.core.world;

import java.util.Locale;
import java.util.OptionalInt;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.ScheduleMailCommand;
import dev.kage.protocol.ScheduleMailResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Thư hẹn giờ (ADR-297) — {@code /thuhengio <phút> <tên> <tiêu đề> | <nội dung>} (hoặc {@code /mailat}): server giữ thư
 * chữ rồi gửi đúng giờ. Phút 1–10080 (7 ngày), tối đa 5 thư chờ.
 */
final class ScheduledMailCoordinator implements WindowFlow {

    private static final int MAX_MINUTES_DIGITS = 5;

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    /** Một lệnh đã tách. */
    record Parsed(int minutes, String recipient, String subject, String body) {}

    ScheduledMailCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!"/thuhengio".equals(command) && !"/mailat".equals(command)) {
            return false;
        }
        java.util.Optional<Parsed> parsed = parse(words.length > 1 ? words[1] : "");
        if (parsed.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "mailat.usage"));
            return true;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_SCHEDULE_MAIL_COMMAND_VALUE,
                ScheduleMailCommand.newBuilder()
                        .setDelayMinutes(parsed.get().minutes())
                        .setRecipientName(parsed.get().recipient())
                        .setSubject(parsed.get().subject())
                        .setBody(parsed.get().body())
                        .build());
        return true;
    }

    /** {@code <phút> <tên> <tiêu đề> | <nội dung>} — thiếu "|" thì nội dung rỗng. Tĩnh để test. */
    static java.util.Optional<Parsed> parse(String args) {
        String[] parts = args.strip().split("\\s+", 3);
        if (parts.length < 3) {
            return java.util.Optional.empty();
        }
        OptionalInt minutes = digits(parts[0]);
        if (minutes.isEmpty()) {
            return java.util.Optional.empty();
        }
        int bar = parts[2].indexOf('|');
        String subject = (bar < 0 ? parts[2] : parts[2].substring(0, bar)).strip();
        String body = bar < 0 ? "" : parts[2].substring(bar + 1).strip();
        return subject.isEmpty()
                ? java.util.Optional.empty()
                : java.util.Optional.of(new Parsed(minutes.getAsInt(), parts[1], subject, body));
    }

    private static OptionalInt digits(String text) {
        if (text.isEmpty()
                || text.length() > MAX_MINUTES_DIGITS
                || !text.chars().allMatch(Character::isDigit)) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(Integer.parseInt(text));
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_SCHEDULE_MAIL_RESPONSE_VALUE) {
            return false;
        }
        ScheduleMailResponse response = ScheduleMailResponse.parseFrom(frame.payload());
        String key = "mailat."
                + response.getOutcome()
                        .name()
                        .replace("SCHEDULE_MAIL_OUTCOME_", "")
                        .toLowerCase(Locale.ROOT);
        chat.showSystemNotice(KageI18n.formatOrKey(i18n, key, response.getPending()));
        return true;
    }
}

package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Chuyển tiếp thư (ADR-295) — {@code /chuyentiep <tên>} (hoặc {@code /forward}): gửi lại tiêu đề + nội dung của lá thư
 * ĐANG CHỌN trong hòm thư cho người khác, KHÔNG kèm vàng/đồ. Thuần client: đi đúng lệnh gửi thư thường (tem thư, chặn
 * người lạ, hộp đầy do server quyết); kết quả hiện như mọi lần gửi thư.
 */
final class MailForwardCoordinator implements WindowFlow {

    /** Tiền tố tiêu đề. */
    static final String PREFIX = "Fwd: ";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private Supplier<Optional<MailController.Mail>> selected = Optional::empty;

    MailForwardCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    /** Nối lá thư đang chọn của hòm thư. */
    void useMail(Supplier<Optional<MailController.Mail>> selectedMail) {
        this.selected = selectedMail;
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!"/chuyentiep".equals(command) && !"/forward".equals(command)) {
            return false;
        }
        String recipient = words.length > 1 ? words[1].strip() : "";
        Optional<MailController.Mail> mail = selected.get();
        if (recipient.isEmpty() || mail.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "forward.usage"));
            return true;
        }
        sender.sendMail(draft(recipient, mail.get()), UUID.randomUUID().toString());
        chat.showSystemNotice(KageI18n.formatOrKey(i18n, "forward.sent", recipient));
        return true;
    }

    /** Bản nháp chuyển tiếp: tiêu đề "Fwd: …" cắt vừa 64, nội dung cắt vừa 1024, không vàng. Tĩnh để test. */
    static MailController.Draft draft(String recipient, MailController.Mail mail) {
        String subject = mail.subject().startsWith(PREFIX) ? mail.subject() : PREFIX + mail.subject();
        return new MailController.Draft(
                recipient,
                cut(subject, MailController.MAX_SUBJECT_LENGTH),
                cut(mail.body(), MailController.MAX_BODY_LENGTH),
                0L);
    }

    private static String cut(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) {
        return false;
    }
}

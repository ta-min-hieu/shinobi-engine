package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ListMailRequest;
import dev.kage.protocol.ListMailResponse;
import dev.kage.protocol.MailView;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Tìm thư theo từ khoá (ADR-275) — {@code /timthu <từ khoá>} (hoặc {@code /findmail}) hỏi lại hộp thư (902, gói sẵn có)
 * rồi in những thư có tiêu đề hoặc nội dung chứa từ khoá (so không dấu). Từ khoá {@code hethong} / {@code system} khớp
 * thư hệ thống. {@code MailView} không mang tên người gửi (chỉ id), nên không lọc theo tên được — xem ADR.
 */
final class MailSearchCoordinator implements WindowFlow {

    static final int MAX_RESULTS = 10;
    static final int SNIPPET = 40;

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private Optional<String> pendingQuery = Optional.empty();

    MailSearchCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    /** Thư khớp {@code query} theo thứ tự hộp thư. Tĩnh để test. */
    static List<MailView> matches(List<MailView> mails, String query) {
        String folded = MentionWatch.fold(query.strip());
        boolean system = "hethong".equals(folded.replace(" ", "")) || "system".equals(folded);
        List<MailView> hits = new ArrayList<>();
        for (MailView mail : mails) {
            boolean text = MentionWatch.fold(mail.getSubject()).contains(folded)
                    || MentionWatch.fold(mail.getBody()).contains(folded);
            if (text || (system && !mail.getHasSender())) {
                hits.add(mail);
            }
        }
        return hits;
    }

    private boolean onSlash(String line) {
        String[] parts = line.strip().split("\\s+", 2);
        String command = parts[0].toLowerCase(Locale.ROOT);
        if (!"/timthu".equals(command) && !"/findmail".equals(command)) {
            return false;
        }
        String query = parts.length > 1 ? parts[1].strip() : "";
        if (query.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "mailsearch.usage"));
            return true;
        }
        pendingQuery = Optional.of(query);
        sender.sendReadRequest(Opcode.OPCODE_LIST_MAIL_REQUEST_VALUE, ListMailRequest.getDefaultInstance());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_LIST_MAIL_RESPONSE_VALUE || pendingQuery.isEmpty()) {
            return false;
        }
        String query = pendingQuery.get();
        pendingQuery = Optional.empty();
        List<MailView> hits =
                matches(ListMailResponse.parseFrom(frame.payload()).getMailsList(), query);
        if (hits.isEmpty()) {
            chat.showSystemNotice(KageI18n.formatOrKey(i18n, "mailsearch.none", query));
            return false;
        }
        chat.showSystemNotice(KageI18n.formatOrKey(i18n, "mailsearch.header", hits.size(), query));
        for (int i = 0; i < Math.min(hits.size(), MAX_RESULTS); i++) {
            MailView mail = hits.get(i);
            String from =
                    KageI18n.textOrKey(i18n, mail.getHasSender() ? "mailsearch.from_player" : "mailsearch.from_system");
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n, "mailsearch.line", i + 1, from, mail.getSubject(), snippet(mail.getBody())));
        }
        return false; // khung thư vẫn nhận danh sách mới như thường
    }

    /** Đoạn đầu nội dung. Tĩnh để test. */
    static String snippet(String body) {
        String flat = body.replace('\n', ' ').strip();
        return flat.length() <= SNIPPET ? flat : flat.substring(0, SNIPPET) + "…";
    }

    @Override
    public void reset() {
        pendingQuery = Optional.empty();
    }
}

package dev.kage.client.core.world;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.BulletinPost;
import dev.kage.protocol.ListBulletinRequest;
import dev.kage.protocol.ListBulletinResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PostBulletinCommand;
import dev.kage.protocol.PostBulletinResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Bảng rao vặt (ADR-336) — {@code /raovat} xem các tin còn hạn, {@code /raovat <chữ>} đăng (thay tin cũ), {@code
 * /raovat xoa} gỡ (hoặc {@code /bulletin}). Mỗi người một tin, sống 24 giờ; muốn mua bán thì nhắn riêng người đăng.
 */
final class BulletinCoordinator implements WindowFlow {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    BulletinCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!"/raovat".equals(command) && !"/bulletin".equals(command)) {
            return false;
        }
        String arg = words.length > 1 ? words[1].strip() : "";
        if (arg.isEmpty()) {
            sender.sendReadRequest(Opcode.OPCODE_LIST_BULLETIN_REQUEST_VALUE, ListBulletinRequest.getDefaultInstance());
            return true;
        }
        String folded = MentionWatch.fold(arg);
        String body = "xoa".equals(folded) || "clear".equals(folded) ? "" : arg;
        sender.sendReadRequest(
                Opcode.OPCODE_POST_BULLETIN_COMMAND_VALUE,
                PostBulletinCommand.newBuilder().setBody(body).build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_POST_BULLETIN_RESPONSE_VALUE) {
            String key = "bulletin."
                    + PostBulletinResponse.parseFrom(frame.payload())
                            .getOutcome()
                            .name()
                            .replace("POST_BULLETIN_OUTCOME_", "")
                            .toLowerCase(Locale.ROOT);
            chat.showSystemNotice(KageI18n.textOrKey(i18n, key));
            return true;
        }
        if (opcode != Opcode.OPCODE_LIST_BULLETIN_RESPONSE_VALUE) {
            return false;
        }
        ListBulletinResponse response = ListBulletinResponse.parseFrom(frame.payload());
        if (response.getPostsCount() == 0) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "bulletin.empty"));
            return true;
        }
        chat.showSystemNotice(KageI18n.textOrKey(i18n, "bulletin.header"));
        ZoneId zone = ZoneId.systemDefault();
        for (BulletinPost post : response.getPostsList()) {
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n,
                    post.getOnline() ? "bulletin.line_online" : "bulletin.line",
                    TIME.format(Instant.ofEpochSecond(post.getPostedAtEpochSeconds())
                            .atZone(zone)),
                    post.getName(),
                    ChatController.sanitize(post.getBody())));
        }
        return true;
    }
}

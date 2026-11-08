package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GuildInviteEvent;
import dev.kage.protocol.InviteToGuildCommand;
import dev.kage.protocol.InviteToGuildOutcome;
import dev.kage.protocol.InviteToGuildResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RespondGuildInviteCommand;
import dev.kage.protocol.RespondGuildInviteOutcome;
import dev.kage.protocol.RespondGuildInviteResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Mời thẳng vào bang (ADR-219) — bang chủ / phó gõ {@code /moibang <tên>} (hoặc {@code /ginvite}); người được mời thấy
 * một dòng chat và gõ {@code /vaobang} ({@code /gaccept}) để vào, {@code /tuchoibang} ({@code /gdecline}) để từ chối.
 * Lời mời hết hạn sau 5 phút.
 */
final class GuildInviteCoordinator implements WindowFlow {

    private static final String INVITE_PREFIX = "INVITE_TO_GUILD_OUTCOME_";
    private static final String RESPOND_PREFIX = "RESPOND_GUILD_INVITE_OUTCOME_";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    GuildInviteCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        switch (words[0].toLowerCase(Locale.ROOT)) {
            case "/moibang", "/ginvite" -> invite(words.length > 1 ? words[1].strip() : "");
            case "/vaobang", "/gaccept" -> respond(true);
            case "/tuchoibang", "/gdecline" -> respond(false);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void invite(String name) {
        if (name.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "guild_invite.usage"));
            return;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_INVITE_TO_GUILD_COMMAND_VALUE,
                InviteToGuildCommand.newBuilder().setTargetName(name).build());
    }

    private void respond(boolean accept) {
        sender.sendReadRequest(
                Opcode.OPCODE_RESPOND_GUILD_INVITE_COMMAND_VALUE,
                RespondGuildInviteCommand.newBuilder().setAccept(accept).build());
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_GUILD_INVITE_EVENT_VALUE) {
            GuildInviteEvent event = GuildInviteEvent.parseFrom(frame.payload());
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n,
                    "guild_invite.received",
                    ChatController.sanitize(event.getInviterName()),
                    ChatController.sanitize(event.getGuildName())));
            return true;
        }
        if (opcode == Opcode.OPCODE_INVITE_TO_GUILD_RESPONSE_VALUE) {
            InviteToGuildOutcome outcome =
                    InviteToGuildResponse.parseFrom(frame.payload()).getOutcome();
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "guild_invite." + keyOf(outcome.name(), INVITE_PREFIX)));
            return true;
        }
        if (opcode == Opcode.OPCODE_RESPOND_GUILD_INVITE_RESPONSE_VALUE) {
            RespondGuildInviteResponse response = RespondGuildInviteResponse.parseFrom(frame.payload());
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n,
                    "guild_invite.respond_" + keyOf(response.getOutcome().name(), RESPOND_PREFIX),
                    ChatController.sanitize(response.getGuildName())));
            if (response.getOutcome() == RespondGuildInviteOutcome.RESPOND_GUILD_INVITE_OUTCOME_JOINED) {
                sender.requestMyGuild();
            }
            return true;
        }
        return false;
    }

    /** Đuôi chữ thường của tên giá trị enum ("OK", "NO_INVITE"…); giá trị lạ ⇒ "unspecified". Tĩnh để test. */
    static String keyOf(String enumName, String prefix) {
        return enumName.startsWith(prefix)
                ? enumName.substring(prefix.length()).toLowerCase(Locale.ROOT)
                : "unspecified";
    }
}

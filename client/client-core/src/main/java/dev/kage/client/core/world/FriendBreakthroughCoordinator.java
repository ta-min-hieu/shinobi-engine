package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.CultivationRealm;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.CongratulateFriendCommand;
import dev.kage.protocol.CongratulateFriendOutcome;
import dev.kage.protocol.CongratulateFriendResponse;
import dev.kage.protocol.FriendBreakthroughEvent;
import dev.kage.protocol.FriendCongratulatedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Báo bạn bè đột phá (ADR-182) — một người bạn mở cảnh giới mới thì in một dòng kèm gợi ý {@code /chucmung <tên>} (hoặc
 * {@code /congrats}); ai chúc mừng mình thì cũng in một dòng. Server giữ nhịp 30 phút và "mỗi người một lần".
 */
final class FriendBreakthroughCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/chucmung";
    private static final String COMMAND_EN = "/congrats";
    private static final String OUTCOME_PREFIX = "CONGRATULATE_FRIEND_OUTCOME_";
    /** Số cấp mỗi cảnh giới (ADR-038 D2) — đổi chỉ số cảnh giới thành cấp mở đầu của nó. */
    private static final int LEVELS_PER_REALM = 18;

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    FriendBreakthroughCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!COMMAND_VI.equals(command) && !COMMAND_EN.equals(command)) {
            return false;
        }
        if (words.length < 2 || words[1].isBlank()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "friend_breakthrough.usage"));
            return true;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_CONGRATULATE_FRIEND_COMMAND_VALUE,
                CongratulateFriendCommand.newBuilder()
                        .setFriendName(words[1].strip())
                        .build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_FRIEND_BREAKTHROUGH_EVENT_VALUE) {
            chat.showSystemNotice(describe(i18n, FriendBreakthroughEvent.parseFrom(frame.payload())));
            return true;
        }
        if (opcode == Opcode.OPCODE_FRIEND_CONGRATULATED_EVENT_VALUE) {
            FriendCongratulatedEvent event = FriendCongratulatedEvent.parseFrom(frame.payload());
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n,
                    "friend_breakthrough.congratulated",
                    ChatController.sanitize(event.getFromName()),
                    realmName(i18n, event.getRealm())));
            return true;
        }
        if (opcode == Opcode.OPCODE_CONGRATULATE_FRIEND_RESPONSE_VALUE) {
            chat.showSystemNotice(describe(i18n, CongratulateFriendResponse.parseFrom(frame.payload())));
            return true;
        }
        return false;
    }

    /** Dòng "bạn X vừa đột phá …" — tĩnh để test không cần màn hình. */
    static String describe(I18NBundle i18n, FriendBreakthroughEvent event) {
        String name = ChatController.sanitize(event.getFriendName());
        return KageI18n.formatOrKey(i18n, "friend_breakthrough.notice", name, realmName(i18n, event.getRealm()));
    }

    /** Dòng kết quả chúc mừng — tĩnh để test không cần màn hình. */
    static String describe(I18NBundle i18n, CongratulateFriendResponse response) {
        CongratulateFriendOutcome outcome = response.getOutcome();
        String suffix = outcome == CongratulateFriendOutcome.UNRECOGNIZED
                ? "unspecified"
                : outcome.name().substring(OUTCOME_PREFIX.length()).toLowerCase(Locale.ROOT);
        return KageI18n.formatOrKey(
                i18n, "friend_breakthrough.outcome." + suffix, ChatController.sanitize(response.getFriendName()));
    }

    private static String realmName(I18NBundle i18n, int realm) {
        return KageI18n.textOrKey(
                i18n,
                CultivationRealm.of(Math.max(0, realm) * LEVELS_PER_REALM + 1).nameKey());
    }
}

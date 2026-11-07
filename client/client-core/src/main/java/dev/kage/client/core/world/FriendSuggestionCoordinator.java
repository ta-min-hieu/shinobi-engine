package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.CultivationRealm;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.FriendSuggestion;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SuggestFriendsRequest;
import dev.kage.protocol.SuggestFriendsResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Gợi ý kết bạn (ADR-211) — {@code /goiyban} (hoặc {@code /suggestfriends}) in tối đa 10 người đang online cùng tầm
 * cảnh giới; {@code /ketban <tên>} (hoặc {@code /addfriend}) gửi lời mời luôn từ khung chat (kết quả hiện qua cửa sổ
 * Bạn bè như khi gõ tên ở đó).
 */
final class FriendSuggestionCoordinator implements WindowFlow {

    private static final String SUGGEST_VI = "/goiyban";
    private static final String SUGGEST_EN = "/suggestfriends";
    private static final String ADD_VI = "/ketban";
    private static final String ADD_EN = "/addfriend";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    FriendSuggestionCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (SUGGEST_VI.equals(command) || SUGGEST_EN.equals(command)) {
            sender.sendReadRequest(
                    Opcode.OPCODE_SUGGEST_FRIENDS_REQUEST_VALUE, SuggestFriendsRequest.getDefaultInstance());
            return true;
        }
        if (ADD_VI.equals(command) || ADD_EN.equals(command)) {
            if (words.length < 2 || words[1].isBlank()) {
                chat.showSystemNotice(KageI18n.textOrKey(i18n, "friend_suggest.add_usage"));
            } else {
                sender.sendFriendRequest(words[1].strip());
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_SUGGEST_FRIENDS_RESPONSE_VALUE) {
            return false;
        }
        chat.showSystemNotice(describe(i18n, SuggestFriendsResponse.parseFrom(frame.payload())));
        return true;
    }

    /** Một dòng: danh sách "Tên (cảnh giới)" hoặc câu "không có ai". Tĩnh để test không cần màn hình. */
    static String describe(I18NBundle i18n, SuggestFriendsResponse response) {
        if (response.getSuggestionsCount() == 0) {
            return KageI18n.textOrKey(i18n, "friend_suggest.none");
        }
        List<String> names = new ArrayList<>();
        for (FriendSuggestion suggestion : response.getSuggestionsList()) {
            names.add(suggestion.getName() + " (" + realmLabel(i18n, suggestion.getLevel()) + ")");
        }
        return KageI18n.formatOrKey(i18n, "friend_suggest.list", String.join(", ", names));
    }

    private static String realmLabel(I18NBundle i18n, int level) {
        CultivationRealm realm = CultivationRealm.of(level);
        String realmName = KageI18n.textOrKey(i18n, realm.nameKey());
        return realm.perfected()
                ? KageI18n.formatOrKey(i18n, "realm.perfect", realmName)
                : KageI18n.formatOrKey(i18n, "realm.tier_short", realmName, realm.tier());
    }
}

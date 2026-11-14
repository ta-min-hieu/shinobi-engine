package dev.kage.client.core.world;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GetCharacterAgeRequest;
import dev.kage.protocol.GetCharacterAgeResponse;
import dev.kage.protocol.GetMutualFriendsRequest;
import dev.kage.protocol.GetMutualFriendsResponse;
import dev.kage.protocol.GetWorldChatHistoryRequest;
import dev.kage.protocol.GetWorldChatHistoryResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Ba lệnh hỏi server lô 60: lịch sử chat thế giới (ADR-408, {@code /lichsuthegioi} hoặc {@code /worldhistory} — các
 * tin tự vào khung chat), bạn chung (ADR-409, {@code /banchung <tên>} hoặc {@code /mutual}), tuổi nhân vật (ADR-410,
 * {@code /tuoi [tên]} hoặc {@code /age}).
 */
final class LookupToolsCoordinator implements WindowFlow {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT);

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    LookupToolsCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        String arg = words.length > 1 ? words[1].strip() : "";
        switch (command) {
            case "/lichsuthegioi", "/worldhistory" ->
                sender.sendReadRequest(
                        Opcode.OPCODE_GET_WORLD_CHAT_HISTORY_REQUEST_VALUE,
                        GetWorldChatHistoryRequest.getDefaultInstance());
            case "/banchung", "/mutual" -> {
                if (arg.isEmpty()) {
                    chat.showSystemNotice(KageI18n.textOrKey(i18n, "mutual.usage"));
                } else {
                    sender.sendReadRequest(
                            Opcode.OPCODE_GET_MUTUAL_FRIENDS_REQUEST_VALUE,
                            GetMutualFriendsRequest.newBuilder()
                                    .setTargetName(arg)
                                    .build());
                }
            }
            case "/tuoi", "/age" ->
                sender.sendReadRequest(
                        Opcode.OPCODE_GET_CHARACTER_AGE_REQUEST_VALUE,
                        GetCharacterAgeRequest.newBuilder().setTargetName(arg).build());
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_GET_WORLD_CHAT_HISTORY_RESPONSE_VALUE) {
            int count = GetWorldChatHistoryResponse.parseFrom(frame.payload()).getCount();
            chat.showSystemNotice(KageI18n.formatOrKey(i18n, "worldhistory.done", count));
            return true;
        }
        if (opcode == Opcode.OPCODE_GET_MUTUAL_FRIENDS_RESPONSE_VALUE) {
            showMutual(GetMutualFriendsResponse.parseFrom(frame.payload()));
            return true;
        }
        if (opcode == Opcode.OPCODE_GET_CHARACTER_AGE_RESPONSE_VALUE) {
            GetCharacterAgeResponse response = GetCharacterAgeResponse.parseFrom(frame.payload());
            chat.showSystemNotice(
                    response.getFound()
                            ? KageI18n.formatOrKey(
                                    i18n,
                                    "age.line",
                                    response.getName(),
                                    dateOf(response.getCreatedEpochSeconds(), ZoneId.systemDefault()),
                                    response.getDays())
                            : KageI18n.textOrKey(i18n, "age.not_found"));
            return true;
        }
        return false;
    }

    private void showMutual(GetMutualFriendsResponse response) {
        if (!response.getFound()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "mutual.not_found"));
        } else if (response.getNamesCount() == 0) {
            chat.showSystemNotice(KageI18n.formatOrKey(i18n, "mutual.none", response.getTargetName()));
        } else {
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n,
                    "mutual.line",
                    response.getTargetName(),
                    response.getNamesCount(),
                    String.join(", ", response.getNamesList())));
        }
    }

    /** Ngày {@code dd/MM/yyyy} theo múi giờ cho trước. Tĩnh để test. */
    static String dateOf(long epochSeconds, ZoneId zone) {
        return DATE.format(Instant.ofEpochSecond(epochSeconds).atZone(zone));
    }
}

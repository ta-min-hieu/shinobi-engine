package dev.kage.client.core.world;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GetInspectHistoryRequest;
import dev.kage.protocol.GetInspectHistoryResponse;
import dev.kage.protocol.GetProfileLikersRequest;
import dev.kage.protocol.GetProfileLikersResponse;
import dev.kage.protocol.InspectVisit;
import dev.kage.protocol.MarkAllMailReadCommand;
import dev.kage.protocol.MarkAllMailReadResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.ProfileLiker;
import dev.kage.protocol.SetInspectFriendsOnlyCommand;
import dev.kage.protocol.SetInspectFriendsOnlyResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Lệnh lô 54 phía server: {@code /dochetthu} đọc hết thư (ADR-359, {@code /readall}), {@code /aithich} ai thích mình
 * (ADR-360, {@code /likers}), {@code /aisoi} lịch sử bị soi (ADR-361, {@code /inspectedby}), {@code /chobanbesoi
 * [bat|tat]} chỉ bạn bè được soi (ADR-362, {@code /friendsinspect}).
 */
final class ProfileVisitsCoordinator implements WindowFlow {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    ProfileVisitsCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    /** Lệnh chỉ cần gửi một yêu cầu rỗng: tên lệnh ⇒ (opcode, gói). */
    private static final Map<String, Map.Entry<Integer, Message>> SIMPLE_REQUESTS = Map.of(
            "/dochetthu",
            Map.entry(Opcode.OPCODE_MARK_ALL_MAIL_READ_COMMAND_VALUE, MarkAllMailReadCommand.getDefaultInstance()),
            "/readall",
            Map.entry(Opcode.OPCODE_MARK_ALL_MAIL_READ_COMMAND_VALUE, MarkAllMailReadCommand.getDefaultInstance()),
            "/aithich",
            Map.entry(Opcode.OPCODE_GET_PROFILE_LIKERS_REQUEST_VALUE, GetProfileLikersRequest.getDefaultInstance()),
            "/likers",
            Map.entry(Opcode.OPCODE_GET_PROFILE_LIKERS_REQUEST_VALUE, GetProfileLikersRequest.getDefaultInstance()),
            "/aisoi",
            Map.entry(Opcode.OPCODE_GET_INSPECT_HISTORY_REQUEST_VALUE, GetInspectHistoryRequest.getDefaultInstance()),
            "/inspectedby",
            Map.entry(Opcode.OPCODE_GET_INSPECT_HISTORY_REQUEST_VALUE, GetInspectHistoryRequest.getDefaultInstance()));

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if ("/chobanbesoi".equals(command) || "/friendsinspect".equals(command)) {
            friendsOnly(words.length > 1 ? words[1].strip() : "");
            return true;
        }
        Map.Entry<Integer, Message> request = SIMPLE_REQUESTS.get(command);
        if (request == null) {
            return false;
        }
        sender.sendReadRequest(request.getKey(), request.getValue());
        return true;
    }

    private void friendsOnly(String arg) {
        String folded = MentionWatch.fold(arg);
        SetInspectFriendsOnlyCommand.Builder request = SetInspectFriendsOnlyCommand.newBuilder();
        switch (folded) {
            case "bat", "on" -> request.setEnabled(true);
            case "tat", "off" -> request.setEnabled(false);
            default -> request.setQueryOnly(true);
        }
        sender.sendReadRequest(Opcode.OPCODE_SET_INSPECT_FRIENDS_ONLY_COMMAND_VALUE, request.build());
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_MARK_ALL_MAIL_READ_RESPONSE_VALUE) {
            int marked = MarkAllMailReadResponse.parseFrom(frame.payload()).getMarked();
            chat.showSystemNotice(KageI18n.formatOrKey(i18n, "readall.done", marked));
            sender.listMail(); // hộp thư bỏ nhãn "(mới)"
            return true;
        }
        if (opcode == Opcode.OPCODE_SET_INSPECT_FRIENDS_ONLY_RESPONSE_VALUE) {
            boolean on =
                    SetInspectFriendsOnlyResponse.parseFrom(frame.payload()).getEnabled();
            chat.showSystemNotice(KageI18n.textOrKey(i18n, on ? "friendsinspect.on" : "friendsinspect.off"));
            return true;
        }
        if (opcode == Opcode.OPCODE_GET_PROFILE_LIKERS_RESPONSE_VALUE) {
            likers(GetProfileLikersResponse.parseFrom(frame.payload()));
            return true;
        }
        if (opcode != Opcode.OPCODE_GET_INSPECT_HISTORY_RESPONSE_VALUE) {
            return false;
        }
        GetInspectHistoryResponse response = GetInspectHistoryResponse.parseFrom(frame.payload());
        chat.showSystemNotice(
                KageI18n.textOrKey(i18n, response.getVisitsCount() == 0 ? "inspectedby.empty" : "inspectedby.header"));
        ZoneId zone = ZoneId.systemDefault();
        for (InspectVisit visit : response.getVisitsList()) {
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n,
                    "inspectedby.line",
                    TIME.format(Instant.ofEpochSecond(visit.getAtEpochSeconds()).atZone(zone)),
                    visit.getViewerName()));
        }
        return true;
    }

    private void likers(GetProfileLikersResponse response) {
        chat.showSystemNotice(KageI18n.formatOrKey(i18n, "likers.header", response.getTotal()));
        for (ProfileLiker liker : response.getLikersList()) {
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n,
                    "likers.line",
                    liker.getName(),
                    DATE.format(LocalDate.ofEpochDay(liker.getLikedOnEpochDay()))));
        }
    }
}

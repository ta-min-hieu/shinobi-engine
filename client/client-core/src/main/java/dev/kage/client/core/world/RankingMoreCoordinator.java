package dev.kage.client.core.world;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.DiligenceRank;
import dev.kage.protocol.GetDiligenceRankingRequest;
import dev.kage.protocol.GetDiligenceRankingResponse;
import dev.kage.protocol.GetIntimacyRankingRequest;
import dev.kage.protocol.GetIntimacyRankingResponse;
import dev.kage.protocol.GetOnlineWeekRequest;
import dev.kage.protocol.GetOnlineWeekResponse;
import dev.kage.protocol.IntimacyPair;
import dev.kage.protocol.OnlineDay;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Ba lệnh hỏi server lô 63: xếp hạng chăm chỉ hôm nay (ADR-431, {@code /topchamchi} hoặc {@code /topdiligent}),
 * online 7 ngày (ADR-432, {@code /online7} hoặc {@code /onlineweek}), xếp hạng thân mật (ADR-434, {@code /topthanmat}
 * hoặc {@code /topintimacy}). Báo bang khi đồng môn đột phá (ADR-433) là dòng kênh bang từ server.
 */
final class RankingMoreCoordinator implements WindowFlow {

    private static final int SECONDS_PER_MINUTE = 60;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM", Locale.ROOT);

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    RankingMoreCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String command = line.strip().toLowerCase(Locale.ROOT);
        switch (command) {
            case "/topchamchi", "/topdiligent" ->
                sender.sendReadRequest(
                        Opcode.OPCODE_GET_DILIGENCE_RANKING_REQUEST_VALUE,
                        GetDiligenceRankingRequest.getDefaultInstance());
            case "/online7", "/onlineweek" ->
                sender.sendReadRequest(
                        Opcode.OPCODE_GET_ONLINE_WEEK_REQUEST_VALUE, GetOnlineWeekRequest.getDefaultInstance());
            case "/topthanmat", "/topintimacy" ->
                sender.sendReadRequest(
                        Opcode.OPCODE_GET_INTIMACY_RANKING_REQUEST_VALUE,
                        GetIntimacyRankingRequest.getDefaultInstance());
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_GET_DILIGENCE_RANKING_RESPONSE_VALUE) {
            showDiligence(GetDiligenceRankingResponse.parseFrom(frame.payload()));
        } else if (opcode == Opcode.OPCODE_GET_ONLINE_WEEK_RESPONSE_VALUE) {
            chat.showSystemNotice(describeWeek(i18n, GetOnlineWeekResponse.parseFrom(frame.payload())));
        } else if (opcode == Opcode.OPCODE_GET_INTIMACY_RANKING_RESPONSE_VALUE) {
            showIntimacy(GetIntimacyRankingResponse.parseFrom(frame.payload()));
        } else {
            return false;
        }
        return true;
    }

    private void showDiligence(GetDiligenceRankingResponse response) {
        if (response.getEntriesCount() == 0) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "topdiligent.empty"));
            return;
        }
        chat.showSystemNotice(KageI18n.textOrKey(i18n, "topdiligent.header"));
        int rank = 1;
        for (DiligenceRank entry : response.getEntriesList()) {
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n, "topdiligent.line", rank++, entry.getName(), entry.getSeconds() / SECONDS_PER_MINUTE));
        }
    }

    private void showIntimacy(GetIntimacyRankingResponse response) {
        if (response.getPairsCount() == 0) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "topintimacy.empty"));
            return;
        }
        chat.showSystemNotice(KageI18n.textOrKey(i18n, "topintimacy.header"));
        int rank = 1;
        for (IntimacyPair pair : response.getPairsList()) {
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n, "topintimacy.line", rank++, pair.getFirstName(), pair.getSecondName(), pair.getIntimacy()));
        }
    }

    /** Một dòng 7 ngày: {@code dd/MM N phút, …} cũ trước. Tĩnh để test. */
    static String describeWeek(I18NBundle i18n, GetOnlineWeekResponse response) {
        StringBuilder days = new StringBuilder();
        int total = 0;
        for (OnlineDay day : response.getDaysList()) {
            if (!days.isEmpty()) {
                days.append(", ");
            }
            int minutes = day.getSeconds() / SECONDS_PER_MINUTE;
            total += minutes;
            days.append(DAY.format(LocalDate.ofEpochDay(day.getDayEpochDays())))
                    .append(' ')
                    .append(minutes)
                    .append('\'');
        }
        return KageI18n.formatOrKey(i18n, "onlineweek.line", total, days.toString());
    }
}

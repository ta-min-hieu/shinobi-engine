package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GetReferralRankingRequest;
import dev.kage.protocol.GetReferralRankingResponse;
import dev.kage.protocol.GetStreakRankingRequest;
import dev.kage.protocol.GetStreakRankingResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PlayerHit;
import dev.kage.protocol.ReferralRank;
import dev.kage.protocol.SearchPlayersRequest;
import dev.kage.protocol.SearchPlayersResponse;
import dev.kage.protocol.StreakRank;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Tra cứu người chơi lô 53 — {@code /topchuoi} (ADR-351, {@code /topstreak}), {@code /topgioithieu} (ADR-352, {@code
 * /topreferral}), {@code /timnguoi <chữ>} (ADR-353, {@code /findplayer}). Server làm hết, client chỉ in.
 */
final class PlayerLookupCoordinator implements WindowFlow {

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    PlayerLookupCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        switch (command) {
            case "/topchuoi", "/topstreak" ->
                sender.sendReadRequest(
                        Opcode.OPCODE_GET_STREAK_RANKING_REQUEST_VALUE, GetStreakRankingRequest.getDefaultInstance());
            case "/topgioithieu", "/topreferral" ->
                sender.sendReadRequest(
                        Opcode.OPCODE_GET_REFERRAL_RANKING_REQUEST_VALUE,
                        GetReferralRankingRequest.getDefaultInstance());
            case "/timnguoi", "/findplayer" -> search(words.length > 1 ? words[1].strip() : "");
            default -> {
                return false;
            }
        }
        return true;
    }

    private void search(String query) {
        if (query.length() < 2) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "findplayer.usage"));
            return;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_SEARCH_PLAYERS_REQUEST_VALUE,
                SearchPlayersRequest.newBuilder().setQuery(query).build());
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_GET_STREAK_RANKING_RESPONSE_VALUE) {
            GetStreakRankingResponse response = GetStreakRankingResponse.parseFrom(frame.payload());
            header("topstreak", response.getEntriesCount());
            int rank = 1;
            for (StreakRank entry : response.getEntriesList()) {
                chat.showSystemNotice(
                        KageI18n.formatOrKey(i18n, "topstreak.line", rank++, entry.getName(), entry.getStreak()));
            }
            return true;
        }
        if (opcode == Opcode.OPCODE_GET_REFERRAL_RANKING_RESPONSE_VALUE) {
            GetReferralRankingResponse response = GetReferralRankingResponse.parseFrom(frame.payload());
            header("topreferral", response.getEntriesCount());
            int rank = 1;
            for (ReferralRank entry : response.getEntriesList()) {
                chat.showSystemNotice(
                        KageI18n.formatOrKey(i18n, "topreferral.line", rank++, entry.getName(), entry.getCount()));
            }
            return true;
        }
        if (opcode != Opcode.OPCODE_SEARCH_PLAYERS_RESPONSE_VALUE) {
            return false;
        }
        SearchPlayersResponse response = SearchPlayersResponse.parseFrom(frame.payload());
        header("findplayer", response.getPlayersCount());
        for (PlayerHit hit : response.getPlayersList()) {
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n,
                    hit.getOnline() ? "findplayer.line_online" : "findplayer.line",
                    hit.getName(),
                    hit.getLevel()));
        }
        return true;
    }

    private void header(String prefix, int count) {
        chat.showSystemNotice(KageI18n.textOrKey(i18n, prefix + (count == 0 ? ".empty" : ".header")));
    }
}

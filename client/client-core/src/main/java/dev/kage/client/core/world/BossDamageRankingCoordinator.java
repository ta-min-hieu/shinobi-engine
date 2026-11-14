package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.BossDamageRank;
import dev.kage.protocol.GetBossDamageRankingRequest;
import dev.kage.protocol.GetBossDamageRankingResponse;
import dev.kage.protocol.GetLikeRankingRequest;
import dev.kage.protocol.GetLikeRankingResponse;
import dev.kage.protocol.LikeRank;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Hai bảng xếp hạng lô 47: tổng sát thương boss thế giới {@code /topboss} (ADR-303) và lượt thích hồ sơ
 * {@code /topthich} (hoặc {@code /toplikes}, ADR-305). Mỗi bảng top 10, in vào chat.
 */
final class BossDamageRankingCoordinator implements WindowFlow {

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    BossDamageRankingCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String command = line.strip().toLowerCase(Locale.ROOT);
        if ("/topboss".equals(command)) {
            sender.sendReadRequest(
                    Opcode.OPCODE_GET_BOSS_DAMAGE_RANKING_REQUEST_VALUE,
                    GetBossDamageRankingRequest.getDefaultInstance());
            return true;
        }
        if ("/topthich".equals(command) || "/toplikes".equals(command)) {
            sender.sendReadRequest(
                    Opcode.OPCODE_GET_LIKE_RANKING_REQUEST_VALUE, GetLikeRankingRequest.getDefaultInstance());
            return true;
        }
        return false;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_GET_BOSS_DAMAGE_RANKING_RESPONSE_VALUE) {
            showBoss(GetBossDamageRankingResponse.parseFrom(frame.payload()));
            return true;
        }
        if (opcode == Opcode.OPCODE_GET_LIKE_RANKING_RESPONSE_VALUE) {
            showLikes(GetLikeRankingResponse.parseFrom(frame.payload()));
            return true;
        }
        return false;
    }

    private void showBoss(GetBossDamageRankingResponse response) {
        if (response.getEntriesCount() == 0) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "topboss.empty"));
            return;
        }
        chat.showSystemNotice(KageI18n.textOrKey(i18n, "topboss.header"));
        int rank = 1;
        for (BossDamageRank entry : response.getEntriesList()) {
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n, "topboss.line", rank++, entry.getName(), entry.getTotalDamage(), entry.getKills()));
        }
    }

    private void showLikes(GetLikeRankingResponse response) {
        if (response.getEntriesCount() == 0) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "toplikes.empty"));
            return;
        }
        chat.showSystemNotice(
                KageI18n.textOrKey(i18n, response.getWeekly() ? "toplikes.header_week" : "toplikes.header")); // ADR-384
        int rank = 1;
        for (LikeRank entry : response.getEntriesList()) {
            chat.showSystemNotice(
                    KageI18n.formatOrKey(i18n, "toplikes.line", rank++, entry.getName(), entry.getLikes()));
        }
    }
}

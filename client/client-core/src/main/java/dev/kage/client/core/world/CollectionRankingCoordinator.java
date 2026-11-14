package dev.kage.client.core.world;

import java.util.List;
import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GetBossKillRankingRequest;
import dev.kage.protocol.GetBossKillRankingResponse;
import dev.kage.protocol.GetEnhanceRankingRequest;
import dev.kage.protocol.GetEnhanceRankingResponse;
import dev.kage.protocol.GetPetRankingRequest;
import dev.kage.protocol.GetPetRankingResponse;
import dev.kage.protocol.GetQuestRankingRequest;
import dev.kage.protocol.GetQuestRankingResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.ScoreEntry;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Bốn bảng xếp hạng lô 64: linh thú (ADR-439, {@code /toplinhthu} hoặc {@code /toppets}), cường hoá (ADR-440, {@code
 * /topcuonghoa} hoặc {@code /topenhance}), nhiệm vụ (ADR-441, {@code /topnhiemvu} hoặc {@code /topquests}), hạ boss
 * (ADR-442, {@code /tophaboss} hoặc {@code /topbosskills}).
 */
final class CollectionRankingCoordinator implements WindowFlow {

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    CollectionRankingCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        switch (line.strip().toLowerCase(Locale.ROOT)) {
            case "/toplinhthu", "/toppets" ->
                sender.sendReadRequest(
                        Opcode.OPCODE_GET_PET_RANKING_REQUEST_VALUE, GetPetRankingRequest.getDefaultInstance());
            case "/topcuonghoa", "/topenhance" ->
                sender.sendReadRequest(
                        Opcode.OPCODE_GET_ENHANCE_RANKING_REQUEST_VALUE, GetEnhanceRankingRequest.getDefaultInstance());
            case "/topnhiemvu", "/topquests" ->
                sender.sendReadRequest(
                        Opcode.OPCODE_GET_QUEST_RANKING_REQUEST_VALUE, GetQuestRankingRequest.getDefaultInstance());
            case "/tophaboss", "/topbosskills" ->
                sender.sendReadRequest(
                        Opcode.OPCODE_GET_BOSS_KILL_RANKING_REQUEST_VALUE,
                        GetBossKillRankingRequest.getDefaultInstance());
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_GET_PET_RANKING_RESPONSE_VALUE) {
            show("toppets", GetPetRankingResponse.parseFrom(frame.payload()).getEntriesList());
        } else if (opcode == Opcode.OPCODE_GET_ENHANCE_RANKING_RESPONSE_VALUE) {
            show(
                    "topenhance",
                    GetEnhanceRankingResponse.parseFrom(frame.payload()).getEntriesList());
        } else if (opcode == Opcode.OPCODE_GET_QUEST_RANKING_RESPONSE_VALUE) {
            show("topquests", GetQuestRankingResponse.parseFrom(frame.payload()).getEntriesList());
        } else if (opcode == Opcode.OPCODE_GET_BOSS_KILL_RANKING_RESPONSE_VALUE) {
            show(
                    "topbosskills",
                    GetBossKillRankingResponse.parseFrom(frame.payload()).getEntriesList());
        } else {
            return false;
        }
        return true;
    }

    /** Tiêu đề {@code <key>.header}, mỗi dòng {@code <key>.line} (hạng, tên, điểm), rỗng {@code <key>.empty}. */
    private void show(String key, List<ScoreEntry> entries) {
        if (entries.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, key + ".empty"));
            return;
        }
        chat.showSystemNotice(KageI18n.textOrKey(i18n, key + ".header"));
        int rank = 1;
        for (ScoreEntry entry : entries) {
            chat.showSystemNotice(KageI18n.formatOrKey(i18n, key + ".line", rank++, entry.getName(), entry.getValue()));
        }
    }
}

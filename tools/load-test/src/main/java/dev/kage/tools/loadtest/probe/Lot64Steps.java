package dev.kage.tools.loadtest.probe;

import java.io.IOException;
import java.util.List;

import com.google.protobuf.MessageLite;

import dev.kage.protocol.Opcode;
import dev.kage.protocol.ScoreEntry;

/**
 * Bước lô 64: xếp hạng linh thú {@code topPets:}, cường hoá {@code topEnhance:}, nhiệm vụ {@code topQuests:}, hạ boss
 * {@code topBossKills:} (ADR-439…442).
 */
final class Lot64Steps {

    private final Lot23Scenario.Asker asker;

    Lot64Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
    }

    String step(String verb) throws IOException {
        return switch (verb) {
            case "topPets" ->
                ask(
                        Opcode.OPCODE_GET_PET_RANKING_REQUEST_VALUE,
                        dev.kage.protocol.GetPetRankingRequest.getDefaultInstance(),
                        Opcode.OPCODE_GET_PET_RANKING_RESPONSE_VALUE,
                        p -> dev.kage.protocol.GetPetRankingResponse.parseFrom(p)
                                .getEntriesList());
            case "topEnhance" ->
                ask(
                        Opcode.OPCODE_GET_ENHANCE_RANKING_REQUEST_VALUE,
                        dev.kage.protocol.GetEnhanceRankingRequest.getDefaultInstance(),
                        Opcode.OPCODE_GET_ENHANCE_RANKING_RESPONSE_VALUE,
                        p -> dev.kage.protocol.GetEnhanceRankingResponse.parseFrom(p)
                                .getEntriesList());
            case "topQuests" ->
                ask(
                        Opcode.OPCODE_GET_QUEST_RANKING_REQUEST_VALUE,
                        dev.kage.protocol.GetQuestRankingRequest.getDefaultInstance(),
                        Opcode.OPCODE_GET_QUEST_RANKING_RESPONSE_VALUE,
                        p -> dev.kage.protocol.GetQuestRankingResponse.parseFrom(p)
                                .getEntriesList());
            case "topBossKills" ->
                ask(
                        Opcode.OPCODE_GET_BOSS_KILL_RANKING_REQUEST_VALUE,
                        dev.kage.protocol.GetBossKillRankingRequest.getDefaultInstance(),
                        Opcode.OPCODE_GET_BOSS_KILL_RANKING_RESPONSE_VALUE,
                        p -> dev.kage.protocol.GetBossKillRankingResponse.parseFrom(p)
                                .getEntriesList());
            default -> "bước không hiểu";
        };
    }

    /** Đọc danh sách dòng của một phản hồi bảng xếp hạng. */
    @FunctionalInterface
    interface EntriesParser {
        List<ScoreEntry> parse(byte[] payload) throws com.google.protobuf.InvalidProtocolBufferException;
    }

    private String ask(int requestOpcode, MessageLite request, int responseOpcode, EntriesParser parser)
            throws IOException {
        return asker.ask(requestOpcode, request, responseOpcode, p -> {
            StringBuilder out = new StringBuilder("top=");
            for (ScoreEntry entry : parser.parse(p)) {
                out.append(entry.getName()).append(':').append(entry.getValue()).append(' ');
            }
            return out.toString().strip();
        });
    }
}

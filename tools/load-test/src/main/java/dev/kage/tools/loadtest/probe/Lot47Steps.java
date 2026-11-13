package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.Opcode;

/** Bước lô 47: xếp hạng sát thương boss (ADR-303) {@code topBoss}; xếp hạng lượt thích (ADR-305) {@code topLikes}. */
final class Lot47Steps {

    private final Lot23Scenario.Asker asker;

    Lot47Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
    }

    String step(String verb) throws IOException {
        return switch (verb) {
            case "topBoss" ->
                asker.ask(
                        Opcode.OPCODE_GET_BOSS_DAMAGE_RANKING_REQUEST_VALUE,
                        dev.kage.protocol.GetBossDamageRankingRequest.getDefaultInstance(),
                        Opcode.OPCODE_GET_BOSS_DAMAGE_RANKING_RESPONSE_VALUE,
                        p -> "topBoss="
                                + dev.kage.protocol.GetBossDamageRankingResponse.parseFrom(p)
                                        .getEntriesCount());
            case "topLikes" ->
                asker.ask(
                        Opcode.OPCODE_GET_LIKE_RANKING_REQUEST_VALUE,
                        dev.kage.protocol.GetLikeRankingRequest.getDefaultInstance(),
                        Opcode.OPCODE_GET_LIKE_RANKING_RESPONSE_VALUE,
                        p -> {
                            StringBuilder out = new StringBuilder("topThích=");
                            for (dev.kage.protocol.LikeRank r : dev.kage.protocol.GetLikeRankingResponse.parseFrom(p)
                                    .getEntriesList()) {
                                out.append(' ').append(r.getName()).append(':').append(r.getLikes());
                            }
                            return out.toString();
                        });
            default -> "bước không hiểu";
        };
    }
}

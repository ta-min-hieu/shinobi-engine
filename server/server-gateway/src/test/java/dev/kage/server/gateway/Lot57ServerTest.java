package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.kage.protocol.GetLikeRankingRequest;
import dev.kage.protocol.GetLikeRankingResponse;
import dev.kage.protocol.GuildLevelRank;
import dev.kage.protocol.LikeRank;

/** Lô 57 — thứ tự xếp hạng cấp trong bang (ADR-385), nhánh xếp hạng thích cả đời (ADR-384). */
class Lot57ServerTest {

    @Test
    void ordersByLevelThenName() {
        List<GuildLevelRank> ranks = new ArrayList<>(List.of(rank("Bính", 10), rank("Giáp", 20), rank("Ất", 10)));
        ranks.sort(Lot57Commands.LEVEL_ORDER);
        assertThat(ranks).extracting(GuildLevelRank::getName).containsExactly("Giáp", "Bính", "Ất");
    }

    @Test
    void allTimeRankingPassesThrough() {
        GetLikeRankingResponse allTime = GetLikeRankingResponse.newBuilder()
                .addEntries(LikeRank.newBuilder().setName("A").setLikes(3))
                .build();
        Lot57Commands commands = new Lot57Commands(null, null);
        assertThat(commands.likeRanking(GetLikeRankingRequest.getDefaultInstance(), allTime))
                .isSameAs(allTime);
    }

    private static GuildLevelRank rank(String name, int level) {
        return GuildLevelRank.newBuilder().setName(name).setLevel(level).build();
    }
}

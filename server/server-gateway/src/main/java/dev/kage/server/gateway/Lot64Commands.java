package dev.kage.server.gateway;

import java.util.List;

import dev.kage.protocol.GetBossKillRankingResponse;
import dev.kage.protocol.GetEnhanceRankingResponse;
import dev.kage.protocol.GetPetRankingResponse;
import dev.kage.protocol.GetQuestRankingResponse;
import dev.kage.protocol.ScoreEntry;

/** Lớp lệnh lô 64 (ADR-439…) — MỘT thành phần của {@link Lot63Commands}. Gọi trên Virtual Thread. */
record Lot64Commands(GatewayFacades gateway, ConnectedCharacterRegistry registry) {

    /** ADR-439 — top 10 linh thú. */
    GetPetRankingResponse petRanking() {
        return GetPetRankingResponse.newBuilder()
                .addAllEntries(entries(facades().gear().topPets(), Lot64Commands::fromGear))
                .build();
    }

    /** ADR-440 — top 10 cường hoá. */
    GetEnhanceRankingResponse enhanceRanking() {
        return GetEnhanceRankingResponse.newBuilder()
                .addAllEntries(entries(facades().gear().topEnhance(), Lot64Commands::fromGear))
                .build();
    }

    /** ADR-441 — top 10 nhiệm vụ. */
    GetQuestRankingResponse questRanking() {
        return GetQuestRankingResponse.newBuilder()
                .addAllEntries(entries(facades().quests().top(), r -> score(r.name(), r.value())))
                .build();
    }

    /** ADR-442 — top 10 hạ boss. */
    GetBossKillRankingResponse bossKillRanking() {
        return GetBossKillRankingResponse.newBuilder()
                .addAllEntries(entries(facades().bossKills().top(), r -> score(r.name(), r.value())))
                .build();
    }

    private static ScoreEntry fromGear(dev.kage.inventory.domain.GearRankingPort.Ranked ranked) {
        return score(ranked.name(), ranked.value());
    }

    /** Một dòng bảng. Tĩnh để test. */
    static ScoreEntry score(String name, long value) {
        return ScoreEntry.newBuilder().setName(name).setValue(value).build();
    }

    private static <T> List<ScoreEntry> entries(List<T> rows, java.util.function.Function<T, ScoreEntry> map) {
        return rows.stream().map(map).toList();
    }

    private Lot64Facades facades() {
        return gateway.progression()
                .expansion()
                .lot34()
                .lot35()
                .lot36()
                .lot37()
                .lot38()
                .lot39()
                .lot40()
                .lot41()
                .lot42()
                .lot44()
                .lot45()
                .lot46()
                .lot48()
                .lot49()
                .lot50()
                .lot51()
                .lot52()
                .lot53()
                .lot54()
                .lot56()
                .lot57()
                .lot58()
                .lot59()
                .lot61()
                .lot62()
                .lot63()
                .lot64();
    }
}

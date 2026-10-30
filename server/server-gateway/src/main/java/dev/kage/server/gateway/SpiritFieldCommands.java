package dev.kage.server.gateway;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.Character;
import dev.kage.friend.api.FriendFacade;
import dev.kage.protocol.FieldSeedView;
import dev.kage.protocol.GetSpiritFieldResponse;
import dev.kage.protocol.HarvestPlotResponse;
import dev.kage.protocol.PlantSeedResponse;
import dev.kage.protocol.SpiritFieldOutcome;
import dev.kage.protocol.SpiritPlotView;
import dev.kage.protocol.UnlockPlotResponse;
import dev.kage.protocol.WaterPlotResponse;
import dev.kage.world.api.SpiritFieldFacade;
import dev.kage.world.domain.SpiritFieldPort;
import dev.kage.world.domain.SpiritFieldRules;

/**
 * Linh điền (ADR-119) — chạy trên Virtual Thread. Xem/tưới ruộng người khác chỉ khi là BẠN BÈ (kiểm ở đây, dữ liệu
 * server); gieo/thu hoạch/mở ô chỉ trên ruộng của chính người gửi ({@code characterId} từ session).
 */
final class SpiritFieldCommands {

    private final SpiritFieldFacade fields;
    private final CharacterFacade characters;
    private final FriendFacade friends;
    private final Clock clock;

    SpiritFieldCommands(SpiritFieldFacade fields, CharacterFacade characters, FriendFacade friends, Clock clock) {
        this.fields = fields;
        this.characters = characters;
        this.friends = friends;
        this.clock = clock;
    }

    GetSpiritFieldResponse view(long characterId, String ownerName) {
        Optional<Character> owner =
                ownerName.isBlank() ? characters.findById(characterId) : characters.findByName(ownerName.strip());
        if (owner.isEmpty()) {
            return GetSpiritFieldResponse.newBuilder()
                    .setOutcome(SpiritFieldOutcome.SPIRIT_FIELD_OUTCOME_UNKNOWN_CHARACTER)
                    .build();
        }
        long ownerId = owner.get().id();
        if (ownerId != characterId && !friends.listFriends(characterId).contains(ownerId)) {
            return GetSpiritFieldResponse.newBuilder()
                    .setOutcome(SpiritFieldOutcome.SPIRIT_FIELD_OUTCOME_NOT_FRIEND)
                    .build();
        }
        SpiritFieldPort.Field field = fields.field(ownerId);
        GetSpiritFieldResponse.Builder response = GetSpiritFieldResponse.newBuilder()
                .setOutcome(SpiritFieldOutcome.SPIRIT_FIELD_OUTCOME_OK)
                .setOwnerName(owner.get().name())
                .setPlots(field.plots())
                .setUnlockCost(
                        field.plots() >= SpiritFieldRules.MAX_PLOTS ? 0L : SpiritFieldRules.unlockCost(field.plots()))
                .setServerTimeMs(clock.millis());
        for (SpiritFieldPort.Plot plot : field.planted()) {
            response.addPlanted(SpiritPlotView.newBuilder()
                    .setIndex(plot.index())
                    .setSeedItemTemplateId(plot.seedTemplateId())
                    .setYieldItemTemplateId(plot.yieldTemplateId())
                    .setRipeAtEpochMs(plot.ripeAt().toEpochMilli())
                    .setWatered(plot.wateredBy() != 0L));
        }
        for (SpiritFieldPort.Seed seed : fields.seeds()) {
            response.addSeeds(FieldSeedView.newBuilder()
                    .setItemTemplateId(seed.itemTemplateId())
                    .setGrowMinutes(seed.growMinutes())
                    .setYieldItemTemplateId(seed.yieldTemplateId())
                    .setYieldQuantity(seed.yieldQuantity()));
        }
        return response.build();
    }

    PlantSeedResponse plant(long characterId, int plotIndex, long seedTemplateId) {
        return PlantSeedResponse.newBuilder()
                .setOutcome(wire(fields.plant(characterId, plotIndex, seedTemplateId, now())))
                .build();
    }

    HarvestPlotResponse harvest(long characterId, int plotIndex) {
        return HarvestPlotResponse.newBuilder()
                .setOutcome(wire(fields.harvest(characterId, plotIndex, now())))
                .build();
    }

    WaterPlotResponse water(long characterId, String ownerName, int plotIndex) {
        Optional<Character> owner = characters.findByName(ownerName.strip());
        SpiritFieldOutcome outcome;
        if (owner.isEmpty()) {
            outcome = SpiritFieldOutcome.SPIRIT_FIELD_OUTCOME_UNKNOWN_CHARACTER;
        } else if (owner.get().id() == characterId
                || !friends.listFriends(characterId).contains(owner.get().id())) {
            outcome = SpiritFieldOutcome.SPIRIT_FIELD_OUTCOME_NOT_FRIEND;
        } else {
            outcome = wire(fields.water(characterId, owner.get().id(), plotIndex, now()));
        }
        return WaterPlotResponse.newBuilder().setOutcome(outcome).build();
    }

    UnlockPlotResponse unlock(long characterId) {
        return UnlockPlotResponse.newBuilder()
                .setOutcome(wire(fields.unlockPlot(characterId)))
                .build();
    }

    private Instant now() {
        return clock.instant();
    }

    private static SpiritFieldOutcome wire(SpiritFieldPort.Outcome outcome) {
        return SpiritFieldOutcome.valueOf(
                "SPIRIT_FIELD_OUTCOME_" + outcome.name().toUpperCase(Locale.ROOT));
    }
}

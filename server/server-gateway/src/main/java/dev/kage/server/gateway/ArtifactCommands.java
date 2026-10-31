package dev.kage.server.gateway;

import java.time.Clock;
import java.util.Optional;

import dev.kage.inventory.api.InventoryFacade;
import dev.kage.inventory.domain.EquipmentSlotType;
import dev.kage.inventory.domain.InventorySnapshot;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.protocol.ArtifactOutcome;
import dev.kage.protocol.UseArtifactResponse;
import dev.kage.server.world.ArtifactEffect;
import dev.kage.server.world.WorldFacade;
import dev.kage.world.api.ArtifactFacade;

/**
 * Pháp bảo (ADR-127) — chiêu chủ động của món đang mặc ở ô ARTIFACT. Chạy trên Virtual Thread: đọc ô từ DB (không tin
 * client nói mình mặc gì), hồi chiêu do {@link ArtifactFacade} giữ, hiệu ứng do World áp trên tick thread.
 */
final class ArtifactCommands {

    private final ArtifactFacade artifacts;
    private final InventoryFacade inventory;
    private final Clock clock;

    ArtifactCommands(ArtifactFacade artifacts, InventoryFacade inventory, Clock clock) {
        this.artifacts = artifacts;
        this.inventory = inventory;
        this.clock = clock;
    }

    UseArtifactResponse use(long characterId, Optional<WorldFacade> world) {
        if (world.isEmpty()) {
            return UseArtifactResponse.newBuilder()
                    .setOutcome(ArtifactOutcome.ARTIFACT_OUTCOME_NOT_IN_WORLD)
                    .build();
        }
        InventorySnapshot snapshot = inventory.listInventory(characterId);
        Optional<Long> worn = Optional.ofNullable(snapshot.equipment().slots().get(EquipmentSlotType.ARTIFACT))
                .flatMap(itemId -> snapshot.inventory().findItem(itemId))
                .map(ItemStack::itemTemplateId);
        return switch (artifacts.use(characterId, worn, clock.instant())) {
            case ArtifactFacade.Use.Ready ready -> {
                world.get()
                        .applyArtifact(
                                characterId,
                                ArtifactEffect.valueOf(ready.skill().effect()),
                                ready.skill().magnitudePercent(),
                                ready.skill().durationSeconds());
                yield UseArtifactResponse.newBuilder()
                        .setOutcome(ArtifactOutcome.ARTIFACT_OUTCOME_OK)
                        .setEffect(ready.skill().effect())
                        .setMagnitudePercent(ready.skill().magnitudePercent())
                        .setDurationSeconds(ready.skill().durationSeconds())
                        .setCooldownSeconds(ready.skill().cooldownSeconds())
                        .build();
            }
            case ArtifactFacade.Use.Cooling cooling ->
                UseArtifactResponse.newBuilder()
                        .setOutcome(ArtifactOutcome.ARTIFACT_OUTCOME_COOLDOWN)
                        .setCooldownSeconds((int) cooling.secondsLeft())
                        .build();
            case ArtifactFacade.Use.NoSkill() ->
                UseArtifactResponse.newBuilder()
                        .setOutcome(ArtifactOutcome.ARTIFACT_OUTCOME_NO_ARTIFACT)
                        .build();
        };
    }
}

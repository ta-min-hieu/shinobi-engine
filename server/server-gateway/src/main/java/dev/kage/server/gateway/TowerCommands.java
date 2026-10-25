package dev.kage.server.gateway;

import java.util.Optional;

import dev.kage.protocol.EnterTowerOutcome;
import dev.kage.protocol.EnterTowerResponse;
import dev.kage.protocol.GetTowerStatusResponse;
import dev.kage.protocol.TowerClimberView;
import dev.kage.world.api.TowerFacade;
import dev.kage.world.domain.TowerPort;

/** Trấn Yêu Tháp (ADR-113) cho MỘT kết nối. Chạy trên Virtual Thread (chạm DB, dời World). */
final class TowerCommands {

    private final ConnectedCharacterRegistry registry;
    private final DungeonInstanceRegistry instances;

    TowerCommands(ConnectedCharacterRegistry registry, DungeonInstanceRegistry instances) {
        this.registry = registry;
        this.instances = instances;
    }

    /**
     * Vào tầng kế tiếp. Trả lời SAU khi đã dời World (cùng luật {@code EnterDungeonResponse} — ADR-047): response còn
     * là mốc happens-before cho lệnh kế của client.
     */
    EnterTowerResponse enter(long characterId) {
        Optional<TowerPort.Floor> next = registry.tower.tower().nextFloorOf(characterId);
        if (next.isEmpty()) {
            return EnterTowerResponse.newBuilder()
                    .setOutcome(EnterTowerOutcome.ENTER_TOWER_OUTCOME_COMPLETED)
                    .build();
        }
        TowerPort.Floor floor = next.get();
        DungeonInstanceRegistry.EnterOutcome outcome = instances.enterTower(
                characterId,
                new DungeonInstanceRegistry.TowerFloorPlan(floor.floor(), floor.monsterCode(), floor.monsterCount()));
        EnterTowerOutcome proto =
                switch (outcome) {
                    case OK -> EnterTowerOutcome.ENTER_TOWER_OUTCOME_OK;
                    case IN_DUEL -> EnterTowerOutcome.ENTER_TOWER_OUTCOME_IN_DUEL;
                    default -> EnterTowerOutcome.ENTER_TOWER_OUTCOME_ALREADY_IN_DUNGEON;
                };
        return EnterTowerResponse.newBuilder()
                .setOutcome(proto)
                .setFloor(outcome == DungeonInstanceRegistry.EnterOutcome.OK ? floor.floor() : 0)
                .build();
    }

    GetTowerStatusResponse status(long characterId) {
        TowerFacade tower = registry.tower.tower();
        GetTowerStatusResponse.Builder response = GetTowerStatusResponse.newBuilder()
                .setBestFloor(tower.bestFloorOf(characterId))
                .setTopFloor(tower.topFloor());
        tower.nextFloorOf(characterId).ifPresent(floor -> response.setNextFloor(floor.floor())
                .setNextMonsterCode(floor.monsterCode())
                .setNextMonsterCount(floor.monsterCount()));
        for (TowerPort.Climber climber : tower.top()) {
            response.addTop(TowerClimberView.newBuilder()
                    .setCharacterName(climber.characterName())
                    .setBestFloor(climber.bestFloor()));
        }
        return response.build();
    }
}

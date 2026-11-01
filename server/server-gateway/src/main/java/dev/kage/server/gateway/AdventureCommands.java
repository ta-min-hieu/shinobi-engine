package dev.kage.server.gateway;

import java.util.Optional;
import java.util.function.LongPredicate;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.PositionSnapshot;
import dev.kage.inventory.api.InventoryFacade;
import dev.kage.inventory.domain.ItemCatalogEntry;
import dev.kage.protocol.AbandonBountyResponse;
import dev.kage.protocol.AcceptBountyOutcome;
import dev.kage.protocol.AcceptBountyResponse;
import dev.kage.protocol.BountyView;
import dev.kage.protocol.GetBountyBoardResponse;
import dev.kage.protocol.ListWaypointsResponse;
import dev.kage.protocol.ListZoneMapResponse;
import dev.kage.protocol.TeleportOutcome;
import dev.kage.protocol.TeleportResponse;
import dev.kage.protocol.WaypointView;
import dev.kage.protocol.WorldChangeReason;
import dev.kage.protocol.ZoneRange;
import dev.kage.quest.api.BountyFacade;
import dev.kage.quest.domain.BountyBoard;
import dev.kage.quest.domain.BountyTemplate;
import dev.kage.world.api.WaypointFacade;
import dev.kage.world.api.WorldGeometryFacade;
import dev.kage.world.domain.PvpZone;
import dev.kage.world.domain.SafeZone;
import dev.kage.world.domain.Waypoint;

/**
 * Truyền tống phù (ADR-100) và bảng truy nã (ADR-101) cho MỘT kết nối. Chạy trên Virtual Thread (chạm DB, dời World).
 */
final class AdventureCommands {

    /** Các nguồn dữ liệu — gom để constructor không vượt 4 tham số. */
    record Deps(
            AdventureFacades adventure,
            CharacterFacade characters,
            InventoryFacade inventory,
            MainWorldRelocation relocation,
            LongPredicate outsideMainWorld,
            LongPredicate outlaw) {}

    private final Deps deps;

    AdventureCommands(Deps deps) {
        this.deps = deps;
    }

    static AdventureCommands of(GatewayFacades facades, WorldGateway worldGateway) {
        return new AdventureCommands(new Deps(
                facades.progression().adventure(),
                facades.characterFacade(),
                facades.inventoryFacade(),
                new MainWorldRelocation(
                        worldGateway.mainWorldChannels(), worldGateway.registry(), facades.characterFacade()),
                id -> worldGateway.dungeonInstanceRegistry().isInDungeon(id)
                        || worldGateway.arenaInstanceRegistry().isInDuel(id),
                worldGateway.registry().killingIntent::isOutlaw)); // ADR-142 — Ma Đầu không được truyền tống
    }

    ListWaypointsResponse waypoints(long characterId) {
        WaypointFacade waypoints = deps.adventure().waypoints();
        ListWaypointsResponse.Builder response = ListWaypointsResponse.newBuilder();
        for (Waypoint waypoint : waypoints.waypoints()) {
            response.addWaypoints(WaypointView.newBuilder()
                    .setCode(waypoint.code())
                    .setNameKey(waypoint.nameKey())
                    .setX(waypoint.x())
                    .setOpen(waypoints.isOpen(characterId, waypoint)));
        }
        return response.setTalismans(talismansOf(characterId)).build();
    }

    /** ADR-141 — dải PvP và dải an toàn của world chính cho HUD (hiển thị thuần; luật đòn đánh vẫn ở ADR-137). */
    ListZoneMapResponse zoneMap() {
        WorldGeometryFacade geometry = deps.adventure().geometry();
        ListZoneMapResponse.Builder response = ListZoneMapResponse.newBuilder();
        for (PvpZone zone : geometry.listPvpZones(SafeZone.MAIN_WORLD_CODE)) {
            response.addPvpRanges(range(zone.code(), zone.minX(), zone.maxX()));
        }
        for (SafeZone zone : geometry.listSafeZones(SafeZone.MAIN_WORLD_CODE)) {
            response.addSafeRanges(range(zone.code(), zone.minX(), zone.maxX()));
        }
        return response.build();
    }

    private static ZoneRange range(String code, float minX, float maxX) {
        return ZoneRange.newBuilder().setCode(code).setMinX(minX).setMaxX(maxX).build();
    }

    TeleportResponse teleport(long characterId, String waypointCode) {
        WaypointFacade waypoints = deps.adventure().waypoints();
        Optional<Waypoint> waypoint = waypoints.find(waypointCode);
        TeleportOutcome outcome;
        if (waypoint.isEmpty()) {
            outcome = TeleportOutcome.TELEPORT_OUTCOME_UNKNOWN_WAYPOINT;
        } else if (!waypoints.isOpen(characterId, waypoint.get())) {
            outcome = TeleportOutcome.TELEPORT_OUTCOME_NOT_UNLOCKED;
        } else if (deps.outsideMainWorld().test(characterId)) {
            outcome = TeleportOutcome.TELEPORT_OUTCOME_NOT_IN_MAIN_WORLD;
        } else if (deps.outlaw().test(characterId)) {
            outcome = TeleportOutcome.TELEPORT_OUTCOME_OUTLAW;
        } else if (!waypoints.consumeTalisman(characterId)) {
            outcome = TeleportOutcome.TELEPORT_OUTCOME_NO_TALISMAN;
        } else {
            outcome = move(characterId, waypoint.get());
        }
        return TeleportResponse.newBuilder().setOutcome(outcome).build();
    }

    private TeleportOutcome move(long characterId, Waypoint waypoint) {
        MainWorldRelocation.Outcome moved = deps.relocation()
                .moveTo(
                        characterId,
                        new PositionSnapshot(waypoint.x(), waypoint.y()),
                        WorldChangeReason.WORLD_CHANGE_REASON_TELEPORT);
        if (moved == MainWorldRelocation.Outcome.OK) {
            return TeleportOutcome.TELEPORT_OUTCOME_OK;
        }
        deps.adventure().waypoints().refundTalisman(characterId);
        return TeleportOutcome.TELEPORT_OUTCOME_NOT_IN_MAIN_WORLD;
    }

    GetBountyBoardResponse bountyBoard(long characterId) {
        BountyFacade.Board board = deps.adventure().bounties().board(characterId);
        GetBountyBoardResponse.Builder response = GetBountyBoardResponse.newBuilder()
                .setCompletedToday(board.completedToday())
                .setMaxPerDay(BountyBoard.MAX_COMPLETIONS_PER_DAY);
        for (BountyTemplate bounty : board.bounties()) {
            response.addBounties(BountyView.newBuilder()
                    .setCode(bounty.code())
                    .setMonsterCode(bounty.monsterCode())
                    .setKillCount(bounty.killCount())
                    .setRewardGold(bounty.rewardGold())
                    .setRewardExp(bounty.rewardExp())
                    .setMinLevel(bounty.minLevel()));
        }
        board.active().ifPresent(active -> response.setActiveCode(active.code())
                .setActiveProgress(active.progress())
                .setActiveTarget(active.target()));
        return response.build();
    }

    AcceptBountyResponse acceptBounty(long characterId, String code) {
        int level =
                deps.characters().findById(characterId).map(Character::level).orElse(1);
        return AcceptBountyResponse.newBuilder()
                .setOutcome(AcceptBountyOutcome.valueOf("ACCEPT_BOUNTY_OUTCOME_"
                        + deps.adventure()
                                .bounties()
                                .accept(characterId, code, level)
                                .name()))
                .build();
    }

    AbandonBountyResponse abandonBounty(long characterId) {
        return AbandonBountyResponse.newBuilder()
                .setAbandoned(deps.adventure().bounties().abandon(characterId))
                .build();
    }

    private int talismansOf(long characterId) {
        Optional<Long> templateId = deps.inventory().itemCatalog().entries().stream()
                .filter(entry -> entry.code().equals(WaypointFacade.TALISMAN_CODE))
                .map(ItemCatalogEntry::id)
                .findFirst();
        if (templateId.isEmpty()) {
            return 0;
        }
        return deps.inventory().listInventory(characterId).inventory().items().stream()
                .filter(stack -> stack.itemTemplateId() == templateId.get())
                .mapToInt(stack -> stack.quantity())
                .sum();
    }
}

package dev.kage.server.gateway;

import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.LongPredicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.character.domain.PositionSnapshot;
import dev.kage.checkin.api.ActivityFacade;
import dev.kage.inventory.api.InventoryFacade;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.protocol.GatherHerbOutcome;
import dev.kage.protocol.GatherHerbResponse;
import dev.kage.protocol.HerbGatheredEvent;
import dev.kage.protocol.HerbNodeView;
import dev.kage.protocol.ListHerbNodesResponse;
import dev.kage.protocol.Opcode;
import dev.kage.world.domain.HerbNode;

/**
 * Hái linh thảo (ADR-098 D3) — server giữ bụi, tự đếm {@link #GATHER_SECONDS} giây rồi kiểm lại vị trí và cấp vật phẩm.
 * Vị trí là mẫu world chính của registry; người đang ở hầm ngục/đấu trường không hái được. Bộ hẹn giờ dùng chung một
 * luồng nền của JDK ({@link CompletableFuture#delayedExecutor}), việc cấp đồ chạy trên Virtual Thread.
 */
final class HerbCommands {

    /** Đứng yên ngần này giây để hái xong. */
    static final long GATHER_SECONDS = 3L;

    private static final Logger LOG = LoggerFactory.getLogger(HerbCommands.class);

    private static final Executor DELAYED = CompletableFuture.delayedExecutor(
            GATHER_SECONDS, TimeUnit.SECONDS, Executors.newVirtualThreadPerTaskExecutor());

    private final ConnectedCharacterRegistry registry;
    private final InventoryFacade inventory;
    private final LongPredicate outsideMainWorld;
    private final Clock clock;

    /** @param outsideMainWorld nhân vật đang ở hầm ngục hoặc đấu trường */
    HerbCommands(
            ConnectedCharacterRegistry registry,
            InventoryFacade inventory,
            LongPredicate outsideMainWorld,
            Clock clock) {
        this.registry = registry;
        this.inventory = inventory;
        this.outsideMainWorld = outsideMainWorld;
        this.clock = clock;
    }

    ListHerbNodesResponse list(long characterId) {
        ListHerbNodesResponse.Builder response = ListHerbNodesResponse.newBuilder();
        for (HerbNodes.View view : registry.herbNodes.list(clock.instant())) {
            HerbNode node = view.node();
            response.addNodes(HerbNodeView.newBuilder()
                    .setNodeId(node.id())
                    .setItemCode(node.itemCode())
                    .setX(node.x())
                    .setY(node.y())
                    .setReady(view.ready())
                    .setSecondsUntilReady((int) view.secondsUntilReady())
                    .setDistance(distanceOrUnknown(characterId, node)));
        }
        return response.build();
    }

    GatherHerbResponse gather(long characterId, long nodeId) {
        Optional<HerbNode> node = registry.herbNodes.node(nodeId);
        float distance = node.map(n -> distanceTo(characterId, n)).orElse(Float.MAX_VALUE);
        GatherHerbOutcome outcome = registry.herbNodes.tryStart(characterId, nodeId, distance, clock.instant());
        if (outcome == GatherHerbOutcome.GATHER_HERB_OUTCOME_STARTED) {
            DELAYED.execute(() -> finish(characterId, node.orElseThrow()));
        }
        return GatherHerbResponse.newBuilder().setOutcome(outcome).build();
    }

    // PMD AvoidCatchingGenericException: ranh giới task nền — bụi PHẢI được nhả dù cấp đồ lỗi gì.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void finish(long characterId, HerbNode node) {
        GatherHerbOutcome outcome = GatherHerbOutcome.GATHER_HERB_OUTCOME_MOVED_AWAY;
        try {
            if (distanceTo(characterId, node) <= HerbNodes.REACH_METERS) {
                InventoryFacade.Result added =
                        inventory.addItem(characterId, ItemStack.newItem(node.itemTemplateId(), node.quantity(), 0));
                outcome = added == InventoryFacade.Result.OK
                        ? GatherHerbOutcome.GATHER_HERB_OUTCOME_GATHERED
                        : GatherHerbOutcome.GATHER_HERB_OUTCOME_BAG_FULL;
            }
        } catch (RuntimeException e) {
            LOG.warn("Hái linh thảo lỗi — nhả bụi: characterId={}, node={}", characterId, node.code(), e);
            outcome = GatherHerbOutcome.GATHER_HERB_OUTCOME_UNSPECIFIED;
        } finally {
            registry.herbNodes.finish(
                    characterId, node.id(), outcome == GatherHerbOutcome.GATHER_HERB_OUTCOME_GATHERED, clock.instant());
        }
        boolean gathered = outcome == GatherHerbOutcome.GATHER_HERB_OUTCOME_GATHERED;
        if (gathered) {
            registry.activity.record(characterId, ActivityFacade.Activity.HERB_GATHER); // ADR-106
        }
        registry.sendToCharacterIfOnline(
                characterId,
                Opcode.OPCODE_HERB_GATHERED_EVENT_VALUE,
                0,
                HerbGatheredEvent.newBuilder()
                        .setNodeId(node.id())
                        .setOutcome(outcome)
                        .setItemTemplateId(gathered ? node.itemTemplateId() : 0L)
                        .setQuantity(gathered ? node.quantity() : 0)
                        .build()
                        .toByteArray());
    }

    private float distanceOrUnknown(long characterId, HerbNode node) {
        float distance = distanceTo(characterId, node);
        return distance == Float.MAX_VALUE ? -1f : distance;
    }

    /** Khoảng cách theo X (di chuyển khoá trục X — ADR-033); không ở world chính ⇒ vô cực. */
    private float distanceTo(long characterId, HerbNode node) {
        PositionSnapshot position = registry.lastKnownPosition(characterId);
        if (position == null || outsideMainWorld.test(characterId)) {
            return Float.MAX_VALUE;
        }
        return Math.abs(position.x() - node.x());
    }
}

package dev.kage.server.world;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.server.world.component.HealthComponent;
import dev.kage.server.world.component.MonsterComponent;
import dev.kage.server.world.component.NpcComponent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Area of Interest (SERVER.md §6 — "bắt buộc, không phải tối ưu, ngay từ Phase 3"). Diff cặp
 * (nhân vật, nhân vật), (nhân vật, quái) VÀ (nhân vật, NPC) trong bán kính
 * {@link WorldConstants#AOI_RADIUS_METERS} giữa 2 tick, phát spawn/moved/despawned qua
 * {@link WorldOutboundSink}.
 *
 * <p>KHÔNG phải {@code IteratingKSystem}: AoI so sánh CẶP entity (O(n²)), không xử lý độc lập
 * từng entity như model iterate-1-entity của {@code IteratingKSystem} — dùng class thường, gọi
 * tường minh từ {@link WorldFacade} sau {@code ecsWorld.update()} mỗi tick (đúng thứ tự
 * StateSyncSystem cuối pipeline, ECS.md §6).
 *
 * <p>O(n²) chấp nhận được ở quy mô nhỏ lát cắt này; spatial grid là việc tối ưu Phase 9 nếu số
 * liệu đo thật đòi hỏi (Điều 30 — đo trước, không tối ưu sớm).
 *
 * <p>⚠️ {@code syncCharacterPair}/{@code syncCharacterMonsterPair}/{@code syncCharacterNpcPair} là
 * BẢN SAO cấu trúc gần giống nhau (3 lần) — CHƯA tổng quát hoá thành 1 hàm chung vì mỗi loại có
 * payload snapshot khác nhau (HP quái, không HP NPC, 2 chiều với nhân vật khác) và tổng quát hoá
 * sớm sẽ phải sửa code đã test kỹ (giống lý do KHÔNG đổi {@code WorldFacade.attachCharacter}'s
 * signature ở Phase 5 GAMEPLAY.md §2). Nếu có loại entity thứ 4 cần AoI, ĐÓ là lúc bắt buộc tổng
 * quát hoá (rule of three).
 */
final class AreaOfInterestTracker {

    private static final ComponentMapper<PositionComponent> POSITION =
            ComponentMapper.getFor(PositionComponent.class);
    private static final ComponentMapper<HealthComponent> HEALTH = ComponentMapper.getFor(HealthComponent.class);
    private static final ComponentMapper<MonsterComponent> MONSTER = ComponentMapper.getFor(MonsterComponent.class);
    private static final ComponentMapper<NpcComponent> NPC = ComponentMapper.getFor(NpcComponent.class);

    private final WorldOutboundSink sink;
    // characterId -> tập ID (nhân vật DƯƠNG hoặc quái ÂM) mà nhân vật đó ĐANG thấy. Quái không có
    // entry riêng ở đây — quái không phải "observer", không ai đứng sau nó để nhận Event.
    private final Map<Long, Set<Long>> visibleTo = new HashMap<>();
    // entityId (nhân vật HOẶC quái) -> {x, y} tại lần sync() GẦN NHẤT — PHẢI tách khỏi
    // PositionComponent.prevX/prevY (MovementSystem ghi đè field đó MỖI tick 60Hz, phục vụ nội suy
    // vẽ phía client, không phải "vị trí lúc AoI phát lần cuối"). Từ khi sync() tách khỏi tick loop
    // (WorldConstants.AOI_SYNC_INTERVAL_TICKS), so sánh với prevX/prevY chỉ bắt được chuyển động
    // của ĐÚNG 1 tick ngay trước lúc sync — nhân vật dừng lại đúng lúc không phải tick sync sẽ
    // KHÔNG BAO GIỜ được báo vị trí cuối cho người xung quanh (prevX==x mãi mãi sau khi đứng yên).
    // float[2] cấp phát 1 LẦN lúc thấy entity lần đầu, ghi đè tại chỗ mỗi sync() — không boxing,
    // không cấp phát lặp lại (Điều 31).
    private final Map<Long, float[]> lastSyncedPosition = new HashMap<>();

    AreaOfInterestTracker(WorldOutboundSink sink) {
        this.sink = sink;
    }

    /** Gọi lúc attach — {@link #sync} ở CÙNG tick sẽ tự phát hiện + gửi spawn 2 chiều. */
    void onAttach(long characterId) {
        visibleTo.put(characterId, new HashSet<>());
    }

    /** Gọi lúc detach — báo despawn cho mọi nhân vật đang thấy {@code characterId}. */
    void onDetach(long characterId) {
        lastSyncedPosition.remove(characterId);
        Set<Long> wasVisibleTo = visibleTo.remove(characterId);
        if (wasVisibleTo == null) {
            return;
        }
        for (long otherId : wasVisibleTo) {
            Set<Long> otherVisible = visibleTo.get(otherId);
            if (otherVisible != null) {
                otherVisible.remove(characterId);
            }
            sink.onCharacterDespawned(otherId, characterId);
        }
    }

    /** Gọi lúc quái chết/bị xoá — báo despawn cho MỌI nhân vật đang thấy nó (quét tuyến tính, đủ ở quy mô này). */
    void onMonsterDetach(long monsterId) {
        lastSyncedPosition.remove(monsterId);
        for (Map.Entry<Long, Set<Long>> entry : visibleTo.entrySet()) {
            if (entry.getValue().remove(monsterId)) {
                sink.onMonsterDespawned(entry.getKey(), monsterId);
            }
        }
    }

    /** Nhân vật hiện đang thấy {@code entityId} (nhân vật hoặc quái) — dùng để phát AttackResultEvent đúng người. */
    Set<Long> observersOf(long entityId) {
        HashSet<Long> observers = new HashSet<Long>();
        for (Map.Entry<Long, Set<Long>> entry : visibleTo.entrySet()) {
            if (entry.getValue().contains(entityId)) {
                observers.add(entry.getKey());
            }
        }
        return observers;
    }

    void sync(
            Map<Long, KEntity> entitiesByCharacterId, Map<Long, KEntity> entitiesByMonsterId,
            Map<Long, KEntity> entitiesByNpcId) {
        Long[] characterIds = entitiesByCharacterId.keySet().toArray(new Long[0]);
        for (int i = 0; i < characterIds.length; i++) {
            for (int j = i + 1; j < characterIds.length; j++) {
                syncCharacterPair(entitiesByCharacterId, characterIds[i], characterIds[j]);
            }
        }
        for (long characterId : characterIds) {
            for (long monsterId : entitiesByMonsterId.keySet()) {
                syncCharacterMonsterPair(entitiesByCharacterId, entitiesByMonsterId, characterId, monsterId);
            }
            for (long npcId : entitiesByNpcId.keySet()) {
                syncCharacterNpcPair(entitiesByCharacterId, entitiesByNpcId, characterId, npcId);
            }
        }
        // PHẢI chạy SAU 2 vòng trên (mọi so sánh "đã đổi vị trí" ở lượt sync() này đọc giá trị CŨ
        // trước khi bị ghi đè ở đây) — xem javadoc lastSyncedPosition.
        refreshLastSyncedPositions(entitiesByCharacterId, entitiesByMonsterId);
    }

    private void refreshLastSyncedPositions(
            Map<Long, KEntity> entitiesByCharacterId, Map<Long, KEntity> entitiesByMonsterId) {
        for (Map.Entry<Long, KEntity> entry : entitiesByCharacterId.entrySet()) {
            snapshotPosition(entry.getKey(), POSITION.get(entry.getValue()));
        }
        for (Map.Entry<Long, KEntity> entry : entitiesByMonsterId.entrySet()) {
            snapshotPosition(entry.getKey(), POSITION.get(entry.getValue()));
        }
    }

    private void snapshotPosition(long entityId, PositionComponent pos) {
        float[] snapshot = lastSyncedPosition.computeIfAbsent(entityId, unusedKey -> new float[2]);
        snapshot[0] = pos.x;
        snapshot[1] = pos.y;
    }

    private void syncCharacterPair(Map<Long, KEntity> entitiesByCharacterId, long a, long b) {
        PositionComponent posA = POSITION.get(entitiesByCharacterId.get(a));
        PositionComponent posB = POSITION.get(entitiesByCharacterId.get(b));
        boolean within = distance(posA, posB) <= WorldConstants.AOI_RADIUS_METERS;
        boolean wasVisible = visibleTo.get(a).contains(b);

        if (within && !wasVisible) {
            enterView(a, posA, b, posB);
        } else if (!within && wasVisible) {
            leaveView(a, b);
        } else if (within) {
            notifyMovedIfChanged(a, b, posB);
            notifyMovedIfChanged(b, a, posA);
        }
    }

    private void syncCharacterMonsterPair(
            Map<Long, KEntity> entitiesByCharacterId, Map<Long, KEntity> entitiesByMonsterId, long characterId,
            long monsterId) {
        PositionComponent charPos = POSITION.get(entitiesByCharacterId.get(characterId));
        KEntity monsterEntity = entitiesByMonsterId.get(monsterId);
        PositionComponent monsterPos = POSITION.get(monsterEntity);
        boolean within = distance(charPos, monsterPos) <= WorldConstants.AOI_RADIUS_METERS;
        boolean wasVisible = visibleTo.get(characterId).contains(monsterId);

        if (within && !wasVisible) {
            visibleTo.get(characterId).add(monsterId);
            HealthComponent health = HEALTH.get(monsterEntity);
            MonsterComponent monster = MONSTER.get(monsterEntity);
            sink.onMonsterSpawned(characterId, new WorldOutboundSink.MonsterSnapshot(
                    monsterId, monster.monsterCode, monsterPos.x, monsterPos.y, health.current, health.max));
        } else if (!within && wasVisible) {
            visibleTo.get(characterId).remove(monsterId);
            sink.onMonsterDespawned(characterId, monsterId);
        } else if (within) {
            notifyMonsterMovedIfChanged(characterId, monsterId, monsterPos);
        }
    }

    /** NPC tĩnh — không có nhánh "trong tầm nhìn, đã đổi vị trí" như {@code syncCharacterMonsterPair}. */
    private void syncCharacterNpcPair(
            Map<Long, KEntity> entitiesByCharacterId, Map<Long, KEntity> entitiesByNpcId, long characterId,
            long npcId) {
        PositionComponent charPos = POSITION.get(entitiesByCharacterId.get(characterId));
        KEntity npcEntity = entitiesByNpcId.get(npcId);
        PositionComponent npcPos = POSITION.get(npcEntity);
        boolean within = distance(charPos, npcPos) <= WorldConstants.AOI_RADIUS_METERS;
        boolean wasVisible = visibleTo.get(characterId).contains(npcId);

        if (within && !wasVisible) {
            visibleTo.get(characterId).add(npcId);
            NpcComponent npc = NPC.get(npcEntity);
            sink.onNpcSpawned(characterId, new WorldOutboundSink.NpcSnapshot(npcId, npc.npcCode, npcPos.x, npcPos.y));
        } else if (!within && wasVisible) {
            visibleTo.get(characterId).remove(npcId);
            sink.onNpcDespawned(characterId, npcId);
        }
    }

    private void enterView(long a, PositionComponent posA, long b, PositionComponent posB) {
        visibleTo.get(a).add(b);
        visibleTo.get(b).add(a);
        sink.onCharacterSpawned(a, b, posB.x, posB.y);
        sink.onCharacterSpawned(b, a, posA.x, posA.y);
    }

    private void leaveView(long a, long b) {
        visibleTo.get(a).remove(b);
        visibleTo.get(b).remove(a);
        sink.onCharacterDespawned(a, b);
        sink.onCharacterDespawned(b, a);
    }

    private void notifyMovedIfChanged(long targetCharacterId, long movedCharacterId, PositionComponent moved) {
        if (movedSinceLastSync(movedCharacterId, moved)) {
            sink.onCharacterMoved(targetCharacterId, movedCharacterId, moved.x, moved.y);
        }
    }

    private void notifyMonsterMovedIfChanged(long targetCharacterId, long monsterId, PositionComponent moved) {
        if (movedSinceLastSync(monsterId, moved)) {
            sink.onMonsterMoved(targetCharacterId, monsterId, moved.x, moved.y);
        }
    }

    /** So với vị trí lúc {@link #sync} gần nhất — KHÔNG phải {@code prevX/prevY} (xem javadoc {@link #lastSyncedPosition}). */
    private boolean movedSinceLastSync(long entityId, PositionComponent current) {
        float[] snapshot = lastSyncedPosition.get(entityId);
        return snapshot == null || current.x != snapshot[0] || current.y != snapshot[1];
    }

    private static double distance(PositionComponent a, PositionComponent b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        return Math.sqrt((dx * dx) + (dy * dy));
    }
}

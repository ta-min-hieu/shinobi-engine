package dev.kage.client.core.world;

import java.util.HashMap;
import java.util.Map;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.EcsWorld;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.protocol.CharacterDespawnedEvent;
import dev.kage.protocol.CharacterMovedEvent;
import dev.kage.protocol.CharacterSpawnedEvent;

/**
 * Người chơi KHÁC: gắn/gỡ entity + model, cập nhật vị trí, suy ra idle/run.
 *
 * <p>Lớp thứ ba cùng hình dạng với {@link MonsterPresenter} và {@link NpcPresenter} — "nhận event
 * spawn/moved/despawn của một loại thực thể, giữ kho của nó, đẩy sang {@link WorldModelRenderer} mỗi
 * khung". Tách ra khỏi {@link WorldScreen} vì lớp đó đã <b>vượt</b> trần {@code CyclomaticComplexity}
 * của PMD khi thêm hội thoại (2026-08-09), lần thứ hai trong cùng một ngày; ba nhóm event giống nhau
 * nằm cạnh nhau trong một lớp là chỗ đầu tiên đáng cắt.
 *
 * <p>KHÔNG dự đoán vị trí (CLIENT.md §6 chỉ cho phép dự đoán CHÍNH mình) — chỉ hiển thị vị trí mới
 * nhất, và nội suy giữa hai khung bằng {@code prevX}/{@code prevY} như trước.
 */
final class RemoteCharacterPresenter {

    private static final ComponentMapper<PositionComponent> POSITION = ComponentMapper.getFor(PositionComponent.class);

    private final EcsWorld world;
    private final WorldModelRenderer models;
    private final Map<Long, KEntity> entities = new HashMap<>();
    private final RemoteMotionTracker motionTracker = new RemoteMotionTracker();

    RemoteCharacterPresenter(EcsWorld world, WorldModelRenderer models) {
        this.world = world;
        this.models = models;
    }

    void onSpawned(CharacterSpawnedEvent event) {
        if (entities.containsKey(event.getCharacterId())) {
            return; // đã có — bỏ qua an toàn thay vì tạo entity trùng.
        }
        KEntity entity = world.createEntity();
        PositionComponent position = new PositionComponent();
        position.x = event.getX();
        position.y = event.getY();
        position.prevX = position.x;
        position.prevY = position.y;
        entity.add(position);
        entities.put(event.getCharacterId(), entity);
        models.addCharacter(event.getCharacterId(), CharacterModelKey.of(event.getFaction(), event.getCostumeTier()));
    }

    void onMoved(CharacterMovedEvent event) {
        KEntity entity = entities.get(event.getCharacterId());
        if (entity == null) {
            return; // Moved tới trước Spawned (hiếm qua TCP, nhưng an toàn bỏ qua thay vì crash).
        }
        PositionComponent position = POSITION.get(entity);
        position.x = event.getX();
        position.y = event.getY();
        position.prevX = position.x;
        position.prevY = position.y;
        motionTracker.onPositionUpdate(event.getCharacterId(), event.getX(), event.getY());
        if (models.isDead(event.getCharacterId())) {
            // Giao thức KHÔNG phát CharacterRespawnedEvent cho ai khác ngoài chính người hồi sinh
            // (xem WorldScreen.onCharacterRespawned) — vị trí đổi sau khi chết chỉ có thể là hồi
            // sinh, nên coi gói di chuyển đầu tiên sau "die" là tín hiệu hồi sinh cho nhân vật KHÁC.
            models.resetAfterRespawn(event.getCharacterId());
        }
    }

    void onDespawned(CharacterDespawnedEvent event) {
        KEntity entity = entities.remove(event.getCharacterId());
        if (entity != null) {
            world.removeEntity(entity);
        }
        models.remove(event.getCharacterId());
        motionTracker.remove(event.getCharacterId());
    }

    /** True nếu {@code characterId} là một người chơi KHÁC đang hiển thị. */
    boolean isPresent(long characterId) {
        return entities.containsKey(characterId);
    }

    void tick(float fixedDeltaTime) {
        motionTracker.tick(fixedDeltaTime);
    }

    /**
     * Đẩy vị trí đã nội suy + trạng thái di chuyển sang {@link WorldModelRenderer}, mỗi khung render.
     *
     * <p>{@code z}/{@code grounded} CHƯA broadcast qua {@code CharacterMovedEvent} (ADR-033 mục 8,
     * hoãn có chủ đích — xem movement.proto) nên {@code z} luôn 0 và KHÔNG gọi
     * {@code setAirborne}: người khác nhảy thì mình không thấy, dù server đã mô phỏng đúng cho họ.
     */
    void sync(float alpha) {
        for (Map.Entry<Long, KEntity> entry : entities.entrySet()) {
            long characterId = entry.getKey();
            PositionComponent position = POSITION.get(entry.getValue());
            models.setPosition(
                    characterId,
                    position.prevX + ((position.x - position.prevX) * alpha),
                    position.prevY + ((position.y - position.prevY) * alpha),
                    position.prevZ + ((position.z - position.prevZ) * alpha));
            // Nhân vật khác không có VelocityComponent (chỉ nội suy vị trí, CLIENT.md §7) — suy
            // "đang di chuyển" từ độ lệch vị trí giữa các CharacterMovedEvent thay vào đó.
            models.setMoving(characterId, motionTracker.isMoving(characterId));
        }
    }

    /** Hình thay thế thời trước ADR-031 — vẽ khi không có model nào nạp được. */
    void renderPlaceholder(PlaceholderBoxRenderer renderer, float alpha) {
        for (KEntity entity : entities.values()) {
            renderer.draw(entity, alpha);
        }
    }

    /** Vẽ MỘT hình thay thế cho một entity — {@link WorldScreen} giữ {@code ShapeRenderer}. */
    interface PlaceholderBoxRenderer {
        void draw(KEntity entity, float alpha);
    }
}

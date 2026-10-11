package dev.kage.client.core.world;

import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.utils.LongMap;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.EcsWorld;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.protocol.CharacterAppearanceChangedEvent;
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
    /**
     * {@link LongMap}: {@link #sync} lặp nó MỖI KHUNG — lặp/tra {@code HashMap} khoá {@code Long} cấp
     * phát (iterator + boxing), đo được ở FrameAllocationTest. {@link LongMap#entries()} trả lại cùng
     * một iterator dựng sẵn.
     */
    private final LongMap<KEntity> entities = new LongMap<>();

    /** Cùng tập người chơi với {@link #entities}, dạng click chuột vào được — xem {@link #characters()}. */
    private final Map<Long, CharacterPoint> pickables = new HashMap<>();

    private final RemoteMotionTracker motionTracker = new RemoteMotionTracker();

    private final long localCharacterId;

    /**
     * @param localCharacterId id của CHÍNH người chơi — server không bao giờ gửi spawn của mình cho
     *     mình, nên một gói như thế là gói hỏng và không được dựng thành "người khác".
     */
    RemoteCharacterPresenter(EcsWorld world, WorldModelRenderer models, long localCharacterId) {
        this.world = world;
        this.models = models;
        this.localCharacterId = localCharacterId;
    }

    void onSpawned(CharacterSpawnedEvent event) {
        if (!MalformedPacket.accepts(event, localCharacterId)) {
            return; // id sai không gian hoặc toạ độ NaN/vô cực — xem MalformedPacket.
        }
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
        pickables.put(event.getCharacterId(), new CharacterPoint(entity));
        models.addCharacter(event.getCharacterId(), CharacterModelKey.of(event.getFaction(), event.getCostumeTier()));
    }

    /**
     * Ngoại hình của một nhân vật khác vừa đổi (ADR-045, opcode 307) — thay model TẠI CHỖ.
     *
     * <p>Không đi qua {@link #onSpawned}: entity, vị trí, hướng mặt và trạng thái chết đều phải
     * giữ nguyên, và thế bất biến "spawn = tạo mới" ở {@link #onSpawned} phải còn nguyên vẹn (đó
     * chính là thứ chặn entity trùng).
     *
     * <p>Bỏ qua nếu chưa thấy nhân vật này: event tới trước spawn thì lần spawn sau đã mang ngoại
     * hình mới rồi ({@code ConnectedCharacterRegistry.updateAppearance}), không có gì để vá.
     */
    void onAppearanceChanged(CharacterAppearanceChangedEvent event) {
        if (!entities.containsKey(event.getCharacterId())) {
            return;
        }
        models.replaceCharacterModel(
                event.getCharacterId(), CharacterModelKey.of(event.getFaction(), event.getCostumeTier()));
    }

    void onMoved(CharacterMovedEvent event) {
        KEntity entity = entities.get(event.getCharacterId());
        if (entity == null || !MalformedPacket.accepts(event)) {
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

    /**
     * Máu của một nhân vật KHÁC vừa đổi. Người đang nằm tư thế chết mà máu về lại số dương thì họ vừa
     * hồi sinh — server phát gói này cho người quan sát mỗi lần hồi sinh (2026-10-11). Tín hiệu "gói
     * di chuyển đầu tiên sau khi chết" ở {@link #onMoved} vẫn giữ, nhưng nó không tới khi người đó gục
     * ngay tại điểm hồi sinh và đứng yên: không có gói di chuyển nào để suy ra.
     */
    void onHealthChanged(long characterId, int currentHp) {
        if (currentHp > 0 && entities.containsKey(characterId) && models.isDead(characterId)) {
            models.resetAfterRespawn(characterId);
        }
    }

    void onDespawned(CharacterDespawnedEvent event) {
        KEntity entity = entities.remove(event.getCharacterId());
        pickables.remove(event.getCharacterId());
        if (entity != null) {
            world.removeEntity(entity);
        }
        models.remove(event.getCharacterId());
        motionTracker.remove(event.getCharacterId());
    }

    /**
     * Gỡ MỌI người chơi khác đang hiển thị — dùng khi chính mình vừa đổi World (vào/ra hầm ngục).
     *
     * <p>Cần có vì giao thức không phát despawn cho NGƯỜI ĐI: {@code AreaOfInterestTracker.onDetach}
     * chỉ báo despawn của người rời đi cho những người ở lại, không báo ngược lại (xem
     * {@code DungeonController}). Không gỡ thì dân làng đứng nguyên trong hầm ngục.
     */
    void clear() {
        for (LongMap.Entry<KEntity> entry : entities.entries()) {
            world.removeEntity(entry.value);
            models.remove(entry.key);
            motionTracker.remove(entry.key);
        }
        entities.clear();
        // Cùng tập với entities — bỏ sót ở đây thì người chơi của World CŨ vẫn click trúng được
        // (entity đã gỡ khỏi EcsWorld nhưng Ashley giữ nguyên PositionComponent của nó), và cú click
        // vào chỗ họ từng đứng chọn họ làm đối thủ thách đấu dù họ không còn ở đây.
        pickables.clear();
    }

    /** True nếu {@code characterId} là một người chơi KHÁC đang hiển thị. */
    boolean isPresent(long characterId) {
        return entities.containsKey(characterId);
    }

    /**
     * Người chơi khác dưới dạng điểm click chuột vào được — cho {@link WorldEntityPicker}, thứ đã
     * bắt quái và NPC theo đúng phép chiếu này (2026-08-27, thêm cho luồng thách đấu tay đôi).
     *
     * <p>Không sinh {@link java.util.Map} mới mỗi lần gọi: {@link #pickables} được vá đúng lúc
     * spawn/despawn, và mỗi phần tử đọc thẳng {@link PositionComponent} nên vị trí không bao giờ
     * lệch với bản đang vẽ — cùng lý do {@code MonsterPresenter} không nhân bản kho quái (Điều 31).
     */
    Map<Long, ? extends PickablePoint> characters() {
        return pickables;
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
        for (LongMap.Entry<KEntity> entry : entities.entries()) {
            long characterId = entry.key;
            PositionComponent position = POSITION.get(entry.value);
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

    /**
     * Một người chơi khác, nhìn qua con mắt của {@link WorldEntityPicker}.
     *
     * <p>Đọc {@code x}/{@code y} TẠI CHỖ từ entity thay vì chép lại toạ độ: chép là tạo ra một bản
     * thứ hai phải nhớ cập nhật ở mọi đường vào ({@code onMoved} hôm nay, và mọi đường khác ngày
     * mai) — hai bản sao vị trí lệch nhau là cách chắc chắn để click trúng chỗ nhân vật ĐÃ đứng.
     */
    private record CharacterPoint(KEntity entity) implements PickablePoint {

        @Override
        public float x() {
            return POSITION.get(entity).x;
        }

        @Override
        public float y() {
            return POSITION.get(entity).y;
        }
    }

    /** Vẽ MỘT hình thay thế cho một entity — {@link WorldScreen} giữ {@code ShapeRenderer}. */
    interface PlaceholderBoxRenderer {
        void draw(KEntity entity, float alpha);
    }
}

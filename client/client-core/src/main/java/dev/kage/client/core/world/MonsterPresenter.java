package dev.kage.client.core.world;

import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;

import dev.kage.protocol.MonsterMovedEvent;
import dev.kage.protocol.MonsterSpawnedEvent;

/**
 * Đưa quái lên màn hình: gắn/gỡ model 3D, đồng bộ vị trí–hướng–động tác mỗi khung, và vẽ hình thay
 * thế cho con nào chưa có art (2026-08-09 — trước đó MỌI con quái đều là một khối hộp đỏ).
 *
 * <p>Tách khỏi {@link WorldScreen} vì lớp đó nằm ĐÚNG trần {@code CyclomaticComplexity} của PMD
 * (bài học đã trả giá 2026-08-09: thêm tính năng thẳng vào đó là gãy build gate) — và vì phần lớn
 * việc ở đây suy được từ dữ liệu thuần, test được mà không cần context GL.
 *
 * <p>Trạng thái quái (mã, vị trí, HP) KHÔNG lưu ở đây: {@link CombatHudController} đã giữ đúng một
 * bản cho HUD, và lớp này đọc lại qua {@link #sync}. Hai bản sao của cùng một sự thật là hai bản sẽ
 * lệch nhau.
 */
final class MonsterPresenter {

    /**
     * Chiều cao model quái, world unit — quy ước "cao đúng 1.0 unit, chân ở z=0" của pipeline art
     * (ASSET-LOG.md §Quy ước bắt buộc) áp cho MỌI model, nhân vật lẫn quái. Chỉ dùng để đặt dấu mục
     * tiêu cho khỏi cắm vào đầu con quái, không ảnh hưởng gameplay.
     */
    private static final float MODEL_HEIGHT_UNITS = 1f;

    /** Khoảng hở giữa đỉnh đầu mục tiêu và dấu hiệu bên trên nó. */
    private static final float TARGET_MARKER_GAP_UNITS = 0.35f;

    private static final float TARGET_MARKER_SIZE_UNITS = 0.3f;

    private final WorldModelRenderer models;

    /**
     * Cùng cơ chế suy "đang di chuyển" như nhân vật KHÁC, và cùng lý do: giao thức không gửi vận tốc
     * quái ({@code MonsterMovedEvent} chỉ có {@code x}/{@code y}), nên độ lệch vị trí giữa 2 gói là
     * nguồn duy nhất để chọn clip {@code idle} hay {@code run}. Instance RIÊNG với instance của
     * {@link WorldScreen} dù không gian id đã tách dấu — quyền sở hữu rõ ràng hơn dùng chung, và
     * {@code remove} của bên này không thể lỡ tay xoá nhầm bộ đếm của một nhân vật.
     */
    private final RemoteMotionTracker motionTracker = new RemoteMotionTracker();

    MonsterPresenter(WorldModelRenderer models) {
        this.models = models;
    }

    /** Quái vào tầm nhìn — gắn model nếu mã quái đó đã có art, ngược lại nó sẽ được vẽ bằng hộp. */
    void onSpawned(MonsterSpawnedEvent event) {
        models.addMonsterOrNpc(event.getMonsterId(), MonsterModelKey.of(event.getMonsterCode()));
        motionTracker.onPositionUpdate(event.getMonsterId(), event.getX(), event.getY());
    }

    void onMoved(MonsterMovedEvent event) {
        motionTracker.onPositionUpdate(event.getMonsterId(), event.getX(), event.getY());
    }

    void onDespawned(long monsterId) {
        models.remove(monsterId);
        motionTracker.remove(monsterId);
    }

    /** Gọi mỗi {@code fixedUpdate} — đếm ngược timeout "đã dừng" của {@link RemoteMotionTracker}. */
    void tick(float fixedDeltaTime) {
        motionTracker.tick(fixedDeltaTime);
    }

    /**
     * Đẩy vị trí/hướng/động tác của mọi con quái CÓ model sang {@link WorldModelRenderer}. Gọi mỗi
     * khung render, ngay trước {@code models.render()}.
     *
     * <p>Vị trí KHÔNG nội suy như nhân vật: quái không có {@code PositionComponent} phía client
     * (không có ECS entity nào cho chúng), chỉ có toạ độ mới nhất từ {@code MonsterMovedEvent} — y
     * hệt thứ mà khối hộp đỏ vẫn dùng từ trước, chỉ đổi thứ được vẽ tại đó. Nội suy quái là việc
     * riêng, cùng hạng mục với snapshot buffer ~100ms của CLIENT.md §7 vẫn còn nợ cho nhân vật khác.
     *
     * @param playerX toạ độ {@code x} của chính người chơi — quái quay mặt về phía người chơi, thứ
     *     duy nhất đáng để nó nhìn ({@code AiSystem} cũng chỉ biết "người chơi đang ở đâu", ADR-033).
     */
    void sync(Map<Long, CombatHudController.MonsterState> monsters, float playerX) {
        for (Map.Entry<Long, CombatHudController.MonsterState> entry : monsters.entrySet()) {
            long monsterId = entry.getKey();
            if (!models.hasModel(monsterId)) {
                continue;
            }
            CombatHudController.MonsterState monster = entry.getValue();
            // z = 0: quái không nhảy (VerticalMovementSystem chỉ chạy cho nhân vật) nên luôn ở mặt đất.
            models.setPosition(monsterId, monster.x(), monster.y(), 0f);
            models.setFacingRight(monsterId, playerX > monster.x());
            models.setMoving(monsterId, motionTracker.isMoving(monsterId));
        }
    }

    /**
     * Vẽ những thứ {@link WorldModelRenderer} không vẽ được: khối hộp cho quái CHƯA có model, và dấu
     * hiệu mục tiêu.
     *
     * <p>Dấu mục tiêu phải là một khối RIÊNG bay trên đầu chứ không phải đổi màu con quái như hồi
     * còn vẽ hộp: model 3D lấy màu từ {@code baseColorFactor} trong chính file {@code .glb}
     * (RENDERING.md §12.2 — cel shader không có đường nhuộm màu theo từng instance), nên "con đang
     * nhắm sáng vàng" không còn thực hiện được bằng cách cũ. Người chơi vẫn cần biết mình vừa click
     * trúng con nào ngay tại chỗ nó đứng, không phải liếc lên thanh máu ở giữa-trên màn hình.
     *
     * @param projection ma trận chiếu của nhánh render đang hoạt động — cùng không gian với model,
     *     nếu không hộp và model sẽ đứng lệch nhau.
     */
    void render(
            ShapeRenderer shapeRenderer,
            Matrix4 projection,
            Map<Long, CombatHudController.MonsterState> monsters,
            long targetId) {
        if (monsters.isEmpty()) {
            return;
        }
        // Depth test như DemoPlatforms — hộp quái và dấu mục tiêu phải che/bị che đúng với nhân vật.
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        shapeRenderer.setProjectionMatrix(projection);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (Map.Entry<Long, CombatHudController.MonsterState> entry : monsters.entrySet()) {
            CombatHudController.MonsterState monster = entry.getValue();
            boolean hasModel = models.hasModel(entry.getKey());
            if (!hasModel) {
                drawPlaceholderBox(shapeRenderer, monster);
            }
            if (entry.getKey() == targetId) {
                drawTargetMarker(shapeRenderer, monster, hasModel);
            }
        }
        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    }

    /**
     * Khối ĐỨNG (cao theo trục Z) chứ không phải hình chữ nhật nằm trên mặt phẳng XY: từ ADR-033
     * camera nhìn gần như ngang, hình nằm phẳng chiếu ra chỉ còn một vệt mỏng gần như vô hình.
     * {@code box} nhận toạ độ GÓC và tự đảo dấu depth (xem DemoPlatforms).
     */
    private static void drawPlaceholderBox(ShapeRenderer shapeRenderer, CombatHudController.MonsterState monster) {
        shapeRenderer.setColor(Color.SCARLET);
        shapeRenderer.box(
                monster.x() - (WorldConstants.BOX_SIZE / 2f),
                monster.y() - (WorldConstants.BOX_SIZE / 2f),
                WorldConstants.MONSTER_HEIGHT_UNITS,
                WorldConstants.BOX_SIZE,
                WorldConstants.BOX_SIZE,
                WorldConstants.MONSTER_HEIGHT_UNITS);
    }

    private static void drawTargetMarker(
            ShapeRenderer shapeRenderer, CombatHudController.MonsterState monster, boolean hasModel) {
        // Đặt theo chiều cao của thứ ĐANG THẬT SỰ được vẽ: model cao 1.0 unit, hộp thay thế cao
        // MONSTER_HEIGHT_UNITS. Dùng một con số cho cả hai thì dấu hiệu hoặc lơ lửng quá cao trên
        // model, hoặc cắm vào giữa khối hộp.
        float headHeight = hasModel ? MODEL_HEIGHT_UNITS : WorldConstants.MONSTER_HEIGHT_UNITS;
        float bottom = headHeight + TARGET_MARKER_GAP_UNITS;
        shapeRenderer.setColor(Color.GOLD);
        shapeRenderer.box(
                monster.x() - (TARGET_MARKER_SIZE_UNITS / 2f),
                monster.y() - (TARGET_MARKER_SIZE_UNITS / 2f),
                bottom + TARGET_MARKER_SIZE_UNITS,
                TARGET_MARKER_SIZE_UNITS,
                TARGET_MARKER_SIZE_UNITS,
                TARGET_MARKER_SIZE_UNITS);
    }
}

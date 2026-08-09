package dev.kage.client.core.world;

import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;

import dev.kage.protocol.NpcSpawnedEvent;

/**
 * Đưa NPC lên màn hình (2026-08-09). <b>Trước hôm nay client bỏ qua hoàn toàn</b>
 * {@code NpcSpawnedEvent}: server đã phát nó từ Phase 6, nhưng không có một dòng code client nào
 * đọc, nên ông trưởng làng — cửa vào toàn bộ hệ thống hội thoại và nhiệm vụ — là <b>vô hình</b>.
 *
 * <p>Đơn giản hơn {@link MonsterPresenter} vì giao thức nói vậy: {@code npc.proto} cố ý KHÔNG có
 * {@code NpcMovedEvent} ("NPC tĩnh không bao giờ đổi vị trí ở lát cắt này"), cũng không có HP. Nên ở
 * đây không có bộ theo dõi chuyển động, không thanh máu, không dấu mục tiêu — chỉ vị trí cố định và
 * hướng mặt.
 *
 * <p>Vị trí phải giữ ở lớp này chứ không đọc lại từ đâu khác, khác {@link MonsterPresenter} vốn mượn
 * bản của {@link CombatHudController}: NPC không thuộc HUD combat nên không có sẵn kho nào đang giữ
 * chúng.
 */
final class NpcPresenter {

    /** Vị trí cố định của một NPC — {@code npc.proto} không có event nào đổi được nó. */
    private record NpcState(float x, float y) {}

    private static final float BOX_WIDTH_UNITS = 0.8f;

    /** Thấp hơn khối quái (1.6) để phân biệt được ngay cả khi cả hai đều chưa có model. */
    private static final float BOX_HEIGHT_UNITS = 1.4f;

    private final WorldModelRenderer models;
    private final Map<Long, NpcState> npcs = new HashMap<>();

    NpcPresenter(WorldModelRenderer models) {
        this.models = models;
    }

    void onSpawned(NpcSpawnedEvent event) {
        npcs.put(event.getNpcId(), new NpcState(event.getX(), event.getY()));
        models.addMonsterOrNpc(event.getNpcId(), NpcModelKey.of(event.getNpcCode()));
    }

    void onDespawned(long npcId) {
        npcs.remove(npcId);
        models.remove(npcId);
    }

    /**
     * Đẩy vị trí + hướng mặt sang {@link WorldModelRenderer}, mỗi khung render.
     *
     * <p>Vị trí không đổi nên về lý thuyết đặt một lần lúc spawn là đủ; vẫn gọi mỗi khung vì
     * <b>hướng mặt đi chung một đường</b> với vị trí ({@code setPosition} mới là chỗ áp ma trận
     * hướng — bẫy đã dính thật một lần ở công cụ chẩn đoán hướng nhân vật), và một NPC quay lưng lại
     * khi người chơi đi vòng qua nó thì trông hỏng ngay.
     */
    void sync(float playerX) {
        for (Map.Entry<Long, NpcState> entry : npcs.entrySet()) {
            long npcId = entry.getKey();
            if (!models.hasModel(npcId)) {
                continue;
            }
            NpcState npc = entry.getValue();
            models.setPosition(npcId, npc.x(), npc.y(), 0f);
            models.setFacingRight(npcId, playerX > npc.x());
        }
    }

    /**
     * Hộp thay thế cho NPC chưa có model. Cùng lý do tồn tại như hộp quái: một mã NPC chưa được vẽ
     * phải HIỆN RA (và click được) chứ không được biến mất — vô hình đúng là trạng thái vừa được sửa
     * ở đây, tái tạo lại nó bằng một nhánh fallback im lặng thì vô nghĩa.
     */
    void render(ShapeRenderer shapeRenderer, Matrix4 projection) {
        if (npcs.isEmpty()) {
            return;
        }
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        shapeRenderer.setProjectionMatrix(projection);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        // Xanh ngọc, không đỏ như quái — màu là thứ đầu tiên người chơi đọc được ở khoảng cách xa, và
        // "đánh được hay nói chuyện được" là phân biệt đáng để đọc ngay.
        shapeRenderer.setColor(Color.TEAL);
        for (Map.Entry<Long, NpcState> entry : npcs.entrySet()) {
            if (models.hasModel(entry.getKey())) {
                continue; // Đã có model 3D thật — vẽ thêm hộp là chồng một khối đặc lên chính nó.
            }
            NpcState npc = entry.getValue();
            shapeRenderer.box(
                    npc.x() - (BOX_WIDTH_UNITS / 2f),
                    npc.y() - (BOX_WIDTH_UNITS / 2f),
                    BOX_HEIGHT_UNITS,
                    BOX_WIDTH_UNITS,
                    BOX_WIDTH_UNITS,
                    BOX_HEIGHT_UNITS);
        }
        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    }
}

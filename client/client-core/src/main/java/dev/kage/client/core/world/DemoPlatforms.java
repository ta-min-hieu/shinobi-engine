package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;

import dev.kage.engine.ecs.system.Aabb;
import dev.kage.engine.ecs.system.Platform;

/**
 * Hình minh hoạ cho bệ nhảy demo ([ADR-033](docs/01-architecture/adr/ADR-033-Ninja-School-Style-Movement.md)
 * "Việc cần làm tiếp theo" #7) — toạ độ HARDCODE khớp {@code server-world.WorldFacade.DEMO_PLATFORMS}.
 *
 * <p><b>Không phải nguồn chân lý</b>: server mới là bên quyết định đáp đất/độ cao (Điều 15), lớp này
 * chỉ vẽ cho người chơi THẤY bệ mà vật lý server đã tính. Chưa có packet nào mô tả địa hình — khi có
 * map pilot thật (ADR-033 #7) thì client nhận toạ độ từ server và lớp này bị xoá, không giữ lại.
 *
 * <p>Tách khỏi {@link WorldScreen} để {@code CharacterOrientationDebugTool} vẽ được ĐÚNG hình học
 * này lúc chụp ảnh kiểm chứng — không phải bản sao chép tay dễ lệch khỏi cái chạy thật.
 */
final class DemoPlatforms {

    /** {@code {minX, minY, maxX, maxY, topZ}} — khớp từng dòng với {@code WorldFacade.DEMO_PLATFORMS}. */
    private static final float[][] PLATFORMS = {
        {3f, -0.75f, 7f, 0.75f, 0.8f},
        {9f, -0.75f, 13f, 0.75f, 1.5f},
        {15f, -0.75f, 19f, 0.75f, 2.2f},
    };

    /** Dày bao nhiêu tính XUỐNG từ mặt bệ — thuần thẩm mỹ, vật lý chỉ biết {@code topZ}. */
    private static final float THICKNESS = 0.3f;

    /**
     * Dải nền đất minh hoạ ({@code z=0}) — {@code minX, maxX}. Vật lý coi mặt đất phẳng {@code z=0}
     * là VÔ HẠN ({@code VerticalMovementSystem.GROUND_Z}), nên dải này chỉ để mắt có mốc, không
     * phải biên thật; đi quá dải vẫn đứng trên đất bình thường.
     */
    private static final float GROUND_MIN_X = -30f;

    private static final float GROUND_MAX_X = 60f;

    private DemoPlatforms() {}

    /**
     * Cùng bộ toạ độ trên, đóng gói cho {@code VerticalMovementSystem} phía CLIENT dự đoán cục bộ.
     *
     * <p>⚠️ Client PHẢI nạp đúng danh sách này thì dự đoán nhảy mới khớp server. Thiếu nó, client
     * tính "không có bệ nào" và luôn kéo nhân vật về {@code z=0}, trong khi server báo đang đứng
     * trên bệ — độ lệch (0.8m ở bệ thấp nhất) NHỎ HƠN {@code WorldConstants.RECONCILE_SNAP_THRESHOLD}
     * (1.0) nên cơ chế sửa sai không kích hoạt, và người chơi thấy nhân vật rơi xuyên qua bệ mãi
     * mãi. Đây là bug thật chủ dự án phát hiện khi test tay (2026-08-09).
     */
    static List<Platform> physics() {
        List<Platform> result = new ArrayList<>(PLATFORMS.length);
        for (float[] platform : PLATFORMS) {
            result.add(new Platform(new Aabb(platform[0], platform[1], platform[2], platform[3]), platform[4]));
        }
        return result;
    }

    /**
     * Vẽ khối cho từng bệ bằng {@code projection} của nhánh render đang hoạt động (giống
     * {@code MonsterPresenter.render} — dùng sai ma trận là lệch hẳn không gian chiếu).
     *
     * <p>⚠️ {@link ShapeRenderer#box} nhận toạ độ GÓC (không phải tâm) và tự đảo dấu {@code depth}
     * (khối kéo dài xuống {@code -Z}). Nên truyền thẳng {@code topZ} làm {@code z} là đúng: mặt TRÊN
     * của khối nằm đúng mặt bệ mà nhân vật đứng lên.
     */
    static void render(ShapeRenderer shapeRenderer, Matrix4 projection) {
        // Depth test BẮT BUỘC — đây là hình khối 3D thật phải che/bị che đúng với nhân vật (ADR-031
        // §9 "thứ tự che nhau: depth buffer"). Thiếu dòng này, ShapeRenderer vẽ SAU nên tô đè phẳng
        // lên nhân vật, nhân vật đứng trên bệ biến mất hoàn toàn — đã thấy thật qua ảnh chụp.
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        shapeRenderer.setProjectionMatrix(projection);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Nền đất trước — mặt trên đúng z=0, khớp GROUND_Z của VerticalMovementSystem. Không có nó
        // thì nhân vật trông như đứng lơ lửng giữa nền trời, không có mốc nào để thấy mình cao/thấp.
        shapeRenderer.setColor(Color.FOREST);
        shapeRenderer.box(
                GROUND_MIN_X,
                PLATFORMS[0][1],
                0f,
                GROUND_MAX_X - GROUND_MIN_X,
                PLATFORMS[0][3] - PLATFORMS[0][1],
                THICKNESS);

        shapeRenderer.setColor(Color.OLIVE);
        for (float[] platform : PLATFORMS) {
            shapeRenderer.box(
                    platform[0],
                    platform[1],
                    platform[4],
                    platform[2] - platform[0],
                    platform[3] - platform[1],
                    THICKNESS);
        }
        shapeRenderer.end();
        // Trả lại trạng thái cho HUD Scene2D vẽ sau (UI phẳng, không cần depth) — không để rò rỉ
        // trạng thái GL sang lớp khác.
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    }
}

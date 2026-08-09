package dev.kage.client.core.world;

import java.util.Map;

import com.badlogic.gdx.math.Vector3;

/**
 * Tìm con quái nằm dưới điểm chuột vừa click (chủ dự án yêu cầu 2026-08-09: "click chuột vào quái
 * để chọn mục tiêu"). Trước đó mục tiêu do client TỰ chọn con đầu tiên thấy được
 * ({@code CombatHudController#onMonsterSpawned}) — với 4 điểm spawn quái thì gần như luôn là con
 * sai, và không có cách nào đổi.
 *
 * <p><b>Bắt theo KHÔNG GIAN MÀN HÌNH, không ray-cast 3D</b>: chiếu vị trí quái ra màn hình rồi so
 * hộp bao 2D quanh điểm chiếu. Lý do là quái CHƯA có model 3D (vẽ bằng khối
 * {@code ShapeRenderer.box} trong {@code WorldScreen#drawMonsters}) nên không có hình học thật để
 * ray-cast vào; và cách này dùng chung được cho cả nhánh 3D lẫn nhánh hình thay thế, chỉ cần đổi
 * {@link WorldToScreen} truyền vào. Hệ quả tự nhiên: vùng bắt luôn co giãn đúng theo mức phóng của
 * camera, không cần hằng số pixel nào phụ thuộc độ phân giải.
 *
 * <p>Không phụ thuộc GL — test được thẳng bằng một {@link WorldToScreen} giả
 * ({@code MonsterPickerTest}).
 */
final class MonsterPicker {

    /**
     * Nửa bề rộng vùng bắt, tính theo CHIỀU CAO chiếu ra màn hình của khối quái. Khối quái vuông
     * ({@code BOX_SIZE} 1m) và cao {@code MONSTER_HEIGHT_UNITS} (1.6m) nên bề rộng thật ≈ 0.6 lần
     * chiều cao; 0.45 rộng hơn một chút để click hụt vài pixel vẫn ăn — chọn nhầm mục tiêu là lỗi
     * nhẹ (click lại là xong), click mãi không trúng mới là thứ làm người chơi bỏ cuộc.
     */
    private static final float HALF_WIDTH_PER_HEIGHT = 0.45f;

    /** Nửa chiều cao vùng bắt, cũng theo chiều cao chiếu — nới 15% để mép trên/dưới dễ trúng. */
    private static final float HALF_HEIGHT_PER_HEIGHT = 0.65f;

    /**
     * Sàn tuyệt đối cho vùng bắt, pixel. Quái ở rất xa chiếu ra chỉ còn vài pixel — không có sàn
     * này thì đúng lúc nó bé nhất (lúc người chơi cần chọn từ xa để chạy tới) lại là lúc không thể
     * click trúng.
     */
    private static final float MIN_HALF_EXTENT_PIXELS = 16f;

    // Vector tạm TÁI DÙNG — pick() có thể gọi mỗi lần click, không cấp phát mới (Điều 31).
    private final Vector3 base = new Vector3();
    private final Vector3 top = new Vector3();

    /**
     * @param screenX toạ độ chuột theo trục ngang, pixel.
     * @param screenY toạ độ chuột theo quy ước GL (gốc DƯỚI-trái) — người gọi phải lật {@code y}
     *     của {@code InputProcessor} trước khi truyền vào, xem {@link WorldToScreen}.
     * @return {@code monster_id} của con gần tâm click nhất trong số các con bị trúng, hoặc
     *     {@code 0} nếu click vào chỗ trống ({@code 0} không bao giờ là id thật — quái mang id ÂM,
     *     nhân vật id DƯƠNG, xem combat.proto).
     */
    long pick(Map<Long, CombatHudController.MonsterState> monsters, float screenX, float screenY, WorldToScreen p) {
        long best = 0L;
        // Điểm số = độ lệch chuẩn hoá so với tâm hộp bao; nhỏ hơn = gần tâm hơn. Dùng để phân xử khi
        // hai con chồng lên nhau trên màn hình (xảy ra thật: camera nhìn gần như ngang).
        float bestScore = Float.MAX_VALUE;
        for (Map.Entry<Long, CombatHudController.MonsterState> entry : monsters.entrySet()) {
            CombatHudController.MonsterState monster = entry.getValue();
            base.set(monster.x(), monster.y(), 0f);
            p.project(base);
            top.set(monster.x(), monster.y(), WorldConstants.MONSTER_HEIGHT_UNITS);
            p.project(top);

            float projectedHeight = Math.abs(top.y - base.y);
            float halfWidth = Math.max(MIN_HALF_EXTENT_PIXELS, projectedHeight * HALF_WIDTH_PER_HEIGHT);
            float halfHeight = Math.max(MIN_HALF_EXTENT_PIXELS, projectedHeight * HALF_HEIGHT_PER_HEIGHT);
            float centerX = (base.x + top.x) / 2f;
            float centerY = (base.y + top.y) / 2f;

            float dx = Math.abs(screenX - centerX) / halfWidth;
            float dy = Math.abs(screenY - centerY) / halfHeight;
            if (dx > 1f || dy > 1f) {
                continue; // ngoài hộp bao
            }
            float score = (dx * dx) + (dy * dy);
            if (score < bestScore) {
                bestScore = score;
                best = entry.getKey();
            }
        }
        return best;
    }
}

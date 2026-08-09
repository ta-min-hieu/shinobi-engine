package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;

/**
 * {@link MonsterPicker} không đụng GL — chiếu bằng một {@link WorldToScreen} giả thay cho camera
 * thật, nên chạy được trong test JVM thường (không cần context đồ hoạ), cùng lý do
 * {@code CombatHudControllerTest} test thẳng controller.
 */
class MonsterPickerTest {

    /**
     * Camera giả đơn giản nhất còn giữ đúng đặc tính quan trọng: 50 pixel mỗi world unit, {@code z}
     * đi LÊN màn hình (quy ước GL), gốc thế giới ở giữa khung 800x600. Quái cao 1.6m ⇒ chiếu ra 80
     * pixel, tức vùng bắt ±36px ngang và ±52px dọc quanh tâm khối.
     */
    private static final float PIXELS_PER_UNIT = 50f;

    private static final WorldToScreen FAKE_CAMERA = (Vector3 world) -> {
        float screenX = 400f + (world.x * PIXELS_PER_UNIT);
        float screenY = 300f + (world.z * PIXELS_PER_UNIT);
        world.set(screenX, screenY, 0f);
    };

    private static Map<Long, CombatHudController.MonsterState> monstersAt(float... worldXs) {
        Map<Long, CombatHudController.MonsterState> monsters = new java.util.HashMap<>();
        for (int i = 0; i < worldXs.length; i++) {
            monsters.put(
                    -(long) (i + 1), new CombatHudController.MonsterState("training_dummy", worldXs[i], 0f, 50, 50));
        }
        return monsters;
    }

    /** Tâm khối quái ở world x=0: màn hình x=400, y giữa chân (300) và đỉnh (380) = 340. */
    @Test
    void shouldPickMonsterUnderClick() {
        assertThat(new MonsterPicker().pick(monstersAt(0f), 400f, 340f, FAKE_CAMERA))
                .isEqualTo(-1L);
    }

    @Test
    void shouldReturnZero_whenClickingEmptySpace() {
        assertThat(new MonsterPicker().pick(monstersAt(0f), 700f, 340f, FAKE_CAMERA))
                .isZero();
    }

    @Test
    void shouldReturnZero_whenNoMonsters() {
        assertThat(new MonsterPicker().pick(Map.of(), 400f, 340f, FAKE_CAMERA)).isZero();
    }

    /**
     * Hai con sát nhau (cách 1 world unit = 50px, hẹp hơn vùng bắt ±36px của cả hai cộng lại) —
     * click lệch về phía con thứ hai phải ra ĐÚNG con thứ hai, không phải con đầu tiên mà vòng lặp
     * gặp. Đây là lý do {@code pick} chấm điểm theo độ lệch chứ không trả về ngay khi trúng.
     */
    @Test
    void shouldPickNearestToClick_whenTwoMonstersOverlap() {
        Map<Long, CombatHudController.MonsterState> monsters = monstersAt(0f, 1f);

        assertThat(new MonsterPicker().pick(monsters, 445f, 340f, FAKE_CAMERA)).isEqualTo(-2L);
        assertThat(new MonsterPicker().pick(monsters, 405f, 340f, FAKE_CAMERA)).isEqualTo(-1L);
    }

    /** Click vào chân/đỉnh khối vẫn phải ăn — người chơi nhắm vào cái mình THẤY, không vào tâm hình học. */
    @Test
    void shouldPickMonster_whenClickingItsFeetOrHead() {
        assertThat(new MonsterPicker().pick(monstersAt(0f), 400f, 302f, FAKE_CAMERA))
                .isEqualTo(-1L);
        assertThat(new MonsterPicker().pick(monstersAt(0f), 400f, 378f, FAKE_CAMERA))
                .isEqualTo(-1L);
    }
}

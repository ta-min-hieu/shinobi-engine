package dev.kage.client.core.ui.hud;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;

import dev.kage.client.core.ui.theme.KageSkin;
import dev.kage.client.core.ui.widget.HealthBar;

/** Kích thước + dựng hình dùng chung giữa các cụm của {@link CombatHud} (tách 2026-10-17, dòng 58(d)). */
final class HudLayout {

    static final float BAR_WIDTH = 190f;
    static final float BAR_HEIGHT = 20f;

    /**
     * Vạch EXP mỏng hơn Máu/Khí nhưng KHÔNG mỏng hơn được nữa: 12f (thử đầu tiên) làm nhãn số cao
     * hơn ô chứa và tràn hẳn ra ngoài khung panel — thấy thật trên ảnh chụp preview 2026-08-09.
     * Đi kèm {@link #SMALL_LABEL_SCALE} thu nhỏ chữ cho vừa.
     */
    static final float EXP_BAR_HEIGHT = 16f;

    /** Cỡ chữ (tỉ lệ) của nhãn nhỏ: số trên vạch EXP, dòng độ trễ. */
    static final float SMALL_LABEL_SCALE = 0.7f;

    private static final float NAME_PLATE_PAD_Y = 4f;

    /**
     * Lề ngang của biển tên nút: chữ tiếng Anh ("Flamesphere") rộng ~130px. Lề ngang 4px làm nhãn dài
     * đè lên viền (kiểm sống 2026-10-02).
     */
    private static final float NAME_PLATE_PAD_X = 10f;

    private HudLayout() {}

    /** Chồng nhãn số lên giữa thanh — kiểu Ninja School, số nằm TRONG thanh chứ không dưới thanh. */
    static Stack overlay(HealthBar bar, Label label) {
        label.setAlignment(Align.center);
        Stack stack = new Stack();
        stack.add(bar);
        stack.add(label);
        return stack;
    }

    /** Khung nền cho biển tên — chữ trần trên nền map sáng (cỏ/tuyết) gần như không đọc được. */
    static Table namePlate(KageSkin skin, Label label) {
        label.setAlignment(Align.center);
        Table plate = new Table();
        plate.setBackground(skin.panel);
        plate.add(label).pad(NAME_PLATE_PAD_Y, NAME_PLATE_PAD_X, NAME_PLATE_PAD_Y, NAME_PLATE_PAD_X);
        return plate;
    }
}

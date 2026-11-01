package dev.kage.client.core.ui.theme;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;

/**
 * Khung UI tu tiên "ngọc và lụa" (lô 25) — vẽ bằng {@link Pixmap} lúc dựng skin, chưa có atlas UI. Nền lụa mực xanh,
 * viền ngọc, một sợi chỉ vàng bên trong, và (khung lớn) bốn viên ngọc thoi ở góc.
 *
 * <p>Ảnh ra để làm NINE-PATCH: mọi chi tiết trang trí nằm trong {@link #CORNER_PX} pixel ở mỗi góc/mép, phần giữa
 * chỉ là
 * màu phẳng — kéo giãn bao nhiêu thì viền và ngọc góc vẫn giữ nguyên độ dày (thứ {@code TextureRegionDrawable} cũ không
 * làm được, xem javadoc {@code KageSkin.framed}).
 */
final class JadeSilkFrames {

    /** Bề rộng vùng góc/mép không bị kéo giãn của nine-patch. */
    static final int CORNER_PX = 10;

    /** Viền ngọc dày bao nhiêu pixel; sợi chỉ vàng nằm ngay trong viền, cách một pixel nền. */
    private static final int JADE_PX = 2;

    private static final int GOLD_INSET = JADE_PX + 1;

    /** Bán kính viên ngọc thoi ở góc và tâm của nó (tính từ mép) — gọn trong {@link #CORNER_PX}. */
    private static final int GEM_RADIUS = 4;

    private static final int GEM_CENTRE = 4;

    private JadeSilkFrames() {}

    /** Bộ màu một khung: nền lụa, viền ngọc, chỉ vàng. */
    record Palette(Color silk, Color jade, Color gold) {}

    /**
     * Khung chữ nhật {@code size × size}; {@code gems} = có bốn viên ngọc ở góc (khung cửa sổ) hay không (ô nhỏ, nút).
     * Người gọi sở hữu Pixmap trả về.
     */
    static Pixmap frame(int size, Palette palette, boolean gems) {
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None); // ghi ĐÈ alpha — góc cắt phải trong suốt thật
        pixmap.setColor(palette.jade());
        pixmap.fill();
        pixmap.setColor(palette.silk());
        pixmap.fillRectangle(JADE_PX, JADE_PX, size - JADE_PX * 2, size - JADE_PX * 2);
        pixmap.setColor(palette.gold());
        pixmap.drawRectangle(GOLD_INSET, GOLD_INSET, size - GOLD_INSET * 2, size - GOLD_INSET * 2);
        pixmap.setColor(Color.CLEAR);
        pixmap.drawPixel(0, 0);
        pixmap.drawPixel(size - 1, 0);
        pixmap.drawPixel(0, size - 1);
        pixmap.drawPixel(size - 1, size - 1);
        if (gems) {
            int far = size - 1 - GEM_CENTRE;
            gem(pixmap, GEM_CENTRE, GEM_CENTRE, palette);
            gem(pixmap, far, GEM_CENTRE, palette);
            gem(pixmap, GEM_CENTRE, far, palette);
            gem(pixmap, far, far, palette);
        }
        return pixmap;
    }

    /** Nút tròn: đĩa lụa, vòng ngọc ngoài, vòng chỉ vàng trong. Người gọi sở hữu Pixmap trả về. */
    static Pixmap ring(int diameter, int jadePx, Palette palette) {
        Pixmap pixmap = new Pixmap(diameter, diameter, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        pixmap.setColor(Color.CLEAR);
        pixmap.fill();
        pixmap.setBlending(Pixmap.Blending.SourceOver);
        int centre = diameter / 2;
        int radius = centre - 1;
        pixmap.setColor(palette.jade());
        pixmap.fillCircle(centre, centre, radius);
        pixmap.setColor(palette.gold());
        pixmap.fillCircle(centre, centre, radius - jadePx);
        pixmap.setColor(palette.silk());
        pixmap.fillCircle(centre, centre, radius - jadePx - 1);
        return pixmap;
    }

    /** Ngọc thoi: hình thoi màu ngọc sáng (pha chỉ vàng) với nhân vàng ở tâm. */
    private static void gem(Pixmap pixmap, int x, int y, Palette palette) {
        pixmap.setColor(
                palette.jade().r * 0.5f + 0.5f, palette.jade().g * 0.5f + 0.5f, palette.jade().b * 0.5f + 0.5f, 1f);
        pixmap.fillTriangle(x - GEM_RADIUS, y, x, y - GEM_RADIUS, x + GEM_RADIUS, y);
        pixmap.fillTriangle(x - GEM_RADIUS, y, x, y + GEM_RADIUS, x + GEM_RADIUS, y);
        pixmap.setColor(palette.gold());
        pixmap.drawPixel(x, y);
    }
}

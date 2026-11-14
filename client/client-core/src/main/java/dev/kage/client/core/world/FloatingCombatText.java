package dev.kage.client.core.world;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;

/**
 * Số damage bay lên tại vị trí trúng đòn (kiểu Ninja School Online — ảnh tham chiếu chủ dự án gửi
 * 2026-08-09). Trả lời trực tiếp câu hỏi "mình có đánh trúng không, trúng bao nhiêu" mà trước đó
 * không có bề mặt nào trong game trả lời được.
 *
 * <p><b>Vẽ ở KHÔNG GIAN MÀN HÌNH, neo theo THẾ GIỚI</b>: mỗi khung chiếu lại điểm neo world sang
 * toạ độ màn hình rồi vẽ chữ ở kích thước pixel gốc. Vẽ thẳng trong không gian 3D (như
 * {@link SkillVfxRenderer}) sẽ phải phóng to font theo world unit và chữ nhoè hẳn.
 *
 * <p>KHÔNG sở hữu {@link BitmapFont} — font do {@code CombatHud}/{@code KageSkin} quản lý vòng đời;
 * lớp này chỉ mượn để vẽ (dispose ở đây sẽ phá font của HUD).
 */
final class FloatingCombatText implements Disposable {

    /** Sống bao lâu trước khi biến mất, giây. */
    private static final float LIFETIME_SECONDS = 1.1f;

    /** Bay lên bao nhiêu pixel màn hình trong cả vòng đời. */
    private static final float RISE_PIXELS = 70f;

    /** Bắt đầu mờ dần từ mốc này của vòng đời (0.55 = 55% cuối mới mờ). */
    private static final float FADE_START_RATIO = 0.45f;

    /** Cao hơn gốc entity bao nhiêu world unit — ngang đầu nhân vật, không phải dưới chân. */
    private static final float ANCHOR_HEIGHT_UNITS = 1.9f;

    /** Số damage hiện cùng lúc tối đa; vượt thì cái mới ghi đè cái cũ nhất (không cấp phát thêm). */
    private static final int MAX_CONCURRENT = 24;

    /** Vàng cam — damage do CHÍNH người chơi gây ra, giống bản gốc. */
    static final Color DAMAGE_DEALT = Color.valueOf("FFD24A");

    /** Đỏ — damage NGƯỜI CHƠI phải nhận, phân biệt ngay bằng màu không cần đọc số. */
    static final Color DAMAGE_TAKEN = Color.valueOf("FF5A4A");

    /** Trắng xám — né/trượt, và cả lời nhắc "vì sao đòn vừa rồi không xảy ra" (2026-08-09). */
    static final Color MISS = Color.valueOf("D8D8D8");

    /** Xanh lá sáng — LÊN CẤP. Màu duy nhất không dùng cho damage, để tin vui không lẫn với sát thương. */
    static final Color LEVEL_UP = Color.valueOf("7CFF7C");

    /** ADR-350 — số chí mạng: cam đậm, to hơn. */
    static final Color CRIT = Color.valueOf("FF8C1A");

    /** ADR-350 — hệ số phóng chữ của số chí mạng. */
    static final float CRIT_SCALE = 1.4f;

    /** ADR-331 — chữ "+N EXP" khi nhận kinh nghiệm. */
    static final Color EXP_GAIN = Color.valueOf("C9A2FF");

    /** ADR-379 — chữ "+N linh thạch" khi nhận linh thạch. */
    static final Color GOLD_GAIN = Color.valueOf("FFE27A");

    /** Biểu cảm (ADR-092) — xanh trời, khác hẳn số sát thương. */
    static final Color EMOTE = Color.valueOf("9FD7FF");

    private final BitmapFont font;
    private final SpriteBatch batch = new SpriteBatch();
    private final GlyphLayout layout = new GlyphLayout();
    private final Matrix4 screenProjection = new Matrix4();
    private final Vector3 scratch = new Vector3();
    private final Entry[] pool = new Entry[MAX_CONCURRENT];
    private int nextSlot;

    FloatingCombatText(BitmapFont font) {
        this.font = font;
        for (int i = 0; i < pool.length; i++) {
            pool[i] = new Entry();
        }
    }

    /** Gọi lại mỗi lần đổi kích thước cửa sổ — chữ vẽ theo pixel màn hình nên phụ thuộc kích thước. */
    void resize(int width, int height) {
        screenProjection.setToOrtho2D(0f, 0f, width, height);
    }

    /**
     * @param worldPosition điểm neo; giá trị được SAO CHÉP ngay nên người gọi tái dùng được một
     *     {@link Vector3} tạm, không cần cấp phát mỗi lần trúng đòn (Điều 31).
     */
    void show(String text, Color color, Vector3 worldPosition) {
        show(text, color, worldPosition, 1f);
    }

    /** ADR-350 — như {@link #show(String, Color, Vector3)} nhưng phóng chữ theo {@code scale}. */
    void show(String text, Color color, Vector3 worldPosition, float scale) {
        Entry entry = pool[nextSlot];
        nextSlot = (nextSlot + 1) % pool.length;
        entry.text = text;
        entry.color.set(color);
        entry.worldX = worldPosition.x;
        entry.worldY = worldPosition.y;
        entry.worldZ = worldPosition.z;
        entry.elapsedSeconds = 0f;
        entry.scale = scale;
        entry.alive = true;
    }

    /**
     * @param projector chiếu điểm thế giới sang màn hình — truyền từ {@code WorldScreen} vì camera
     *     đang hoạt động có thể là camera 3D ({@code WorldModelRenderer}) hoặc camera 2D thay
     *     thế, lớp này không cần biết cái nào.
     */
    void render(float deltaTime, WorldToScreen projector) {
        batch.setProjectionMatrix(screenProjection);
        boolean drawing = false;
        float baseScaleX = font.getData().scaleX;
        float baseScaleY = font.getData().scaleY;
        for (Entry entry : pool) {
            if (!entry.alive) {
                continue;
            }
            entry.elapsedSeconds += deltaTime;
            float progress = entry.elapsedSeconds / LIFETIME_SECONDS;
            if (progress >= 1f) {
                entry.alive = false;
                continue;
            }
            if (!drawing) {
                batch.begin();
                drawing = true;
            }
            scratch.set(entry.worldX, entry.worldY, entry.worldZ + ANCHOR_HEIGHT_UNITS);
            projector.project(scratch);

            float alpha =
                    progress < FADE_START_RATIO ? 1f : 1f - ((progress - FADE_START_RATIO) / (1f - FADE_START_RATIO));
            font.getData().setScale(baseScaleX * entry.scale, baseScaleY * entry.scale); // ADR-350
            layout.setText(font, entry.text);
            float x = scratch.x - (layout.width / 2f);
            float y = scratch.y + (RISE_PIXELS * progress);

            // Bản gốc có viền đen quanh chữ; không có shader viền nên vẽ bản tối lệch 2px làm bóng —
            // đủ để số đọc được trên nền sáng (cỏ/tuyết) lẫn nền tối.
            font.setColor(0f, 0f, 0f, alpha * 0.85f);
            font.draw(batch, entry.text, x + 2f, y - 2f);
            font.setColor(entry.color.r, entry.color.g, entry.color.b, alpha);
            font.draw(batch, entry.text, x, y);
        }
        font.getData().setScale(baseScaleX, baseScaleY); // trả cỡ chữ — font dùng CHUNG với HUD
        if (drawing) {
            batch.end();
            font.setColor(Color.WHITE); // trả màu về mặc định — font dùng CHUNG với HUD
        }
    }

    @Override
    public void dispose() {
        batch.dispose(); // KHÔNG dispose font — xem javadoc lớp, font thuộc về HUD
    }

    /** Một số damage đang bay — tái dùng trong {@link #pool}, không bao giờ cấp phát lại. */
    private static final class Entry {
        private String text = "";
        private final Color color = new Color(Color.WHITE);
        private float worldX;
        private float worldY;
        private float worldZ;
        private float elapsedSeconds;
        private float scale = 1f;
        private boolean alive;
    }
}

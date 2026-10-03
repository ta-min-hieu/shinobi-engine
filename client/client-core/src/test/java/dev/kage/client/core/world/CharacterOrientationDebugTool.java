package dev.kage.client.core.world;

import java.util.zip.Deflater;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.client.core.ui.hud.CombatHud;

/**
 * THROWAWAY — chẩn đoán báo cáo thật "nhân vật bị lộn ngược" (2026-08-09, ADR-033 phiên nhảy) qua
 * ảnh chụp thật, không suy đoán (cùng phương pháp {@code CharacterModelDebugTool} trước đây, RENDERING.md
 * §11.1). Chụp cả clip {@code idle} lẫn {@code jump} của CÙNG model để xác định lỗi ở render code
 * (ảnh hưởng cả 2 clip) hay ở asset {@code jump} riêng (chỉ ảnh hưởng 1 clip). XOÁ sau khi dùng xong.
 */
public final class CharacterOrientationDebugTool extends ApplicationAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(CharacterOrientationDebugTool.class);
    private static final long CHARACTER_ID = 1L;
    private static final String OUTPUT_DIR = "E:/study/libgdx/shinobi-engine/.debug-orientation/";

    private WorldModelRenderer models;
    private ShapeRenderer shapeRenderer;
    private SkillVfxRenderer skillVfx;
    private FloatingCombatText floatingText;
    private CombatHud hud;
    private int frame;
    private boolean idleCaptured;

    @Override
    public void create() {
        models = new WorldModelRenderer(GraphicsTier.MEDIUM);
        // ĐÚNG khung nhìn của game thật (WorldConstants.VIEWPORT_HEIGHT_UNITS) — kiểm hiệu ứng ở
        // zoom khác zoom thật thì không kết luận được gì về kích thước/vị trí của nó trên màn.
        models.resize(10f, 10f);
        shapeRenderer = new ShapeRenderer();
        skillVfx = new SkillVfxRenderer(GraphicsTier.MEDIUM);
        // CombatHud chỉ để MƯỢN font (giống WorldScreen thật) — không vẽ HUD ở công cụ này.
        hud = new CombatHud();
        floatingText = new FloatingCombatText(hud.font());
        floatingText.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        models.addCharacter(CHARACTER_ID, "char_hoa-b1");
        if (!models.isEnabled()) {
            LOG.error("Không đo được: thiếu model char_hoa-b1 dưới models/med/.");
            Gdx.app.exit();
            return;
        }
        // Đứng trên NỀN ĐẤT ngay trước bệ đầu tiên (bệ 1 bắt đầu ở x=3) — thấy được cả nền, cả bậc,
        // cả tương quan độ cao trong một khung.
        models.setPosition(CHARACTER_ID, 1f, 0f, 0f);
        models.setMoving(CHARACTER_ID, false);
        models.follow(1f, 0f); // giống WorldScreen: camera bám đúng vị trí nhân vật
    }

    @Override
    public void render() {
        if (models == null || !models.isEnabled()) {
            return;
        }
        frame++;
        // WorldModelRenderer.render() KHÔNG tự clear (trách nhiệm của caller, xem javadoc) —
        // WorldScreen làm việc này ở render() thật, công cụ chẩn đoán này quên mất nên ảnh chụp lần
        // đầu ra toàn đen.
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.15f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        // MỖI FRAME như WorldScreen.renderModels — setPosition mới là nơi áp ma trận hướng
        // mặt, gọi 1 lần lúc create() thì đổi hướng sau đó không có tác dụng gì (bẫy đã dính thật).
        models.setPosition(CHARACTER_ID, 1f, 0f, 0f);
        models.render(1f / 60f);
        // ĐÚNG lớp production WorldScreen gọi (không phải bản chép tay) — ảnh chụp mới kiểm chứng
        // được hình học bệ thật.
        DemoPlatforms.render(shapeRenderer, models.combined());
        skillVfx.render(1f / 60f, models.combined());
        floatingText.render(1f / 60f, models::project);

        if (frame == 90 && !idleCaptured) {
            capture("facing-right");
            idleCaptured = true;
            models.setFacingRight(CHARACTER_ID, false);
            skillVfx.play("fireball", 1f, 0f, 0f); // ngay tại chân nhân vật, giống lúc trúng đòn
            floatingText.show("-152", FloatingCombatText.DAMAGE_DEALT, new com.badlogic.gdx.math.Vector3(1f, 0f, 0f));
            floatingText.show("Né!", FloatingCombatText.MISS, new com.badlogic.gdx.math.Vector3(2.4f, 0f, 0f));
        }
        // Bắt giữa lúc animation VFX đang chạy (12 khung/giây, ~0.25s) — chụp quá muộn thì hiệu ứng
        // đã kết thúc và ảnh không chứng minh được gì.
        if (frame == 96) {
            capture("skill-vfx");
        }
        if (frame == 180) {
            capture("facing-left");
            Gdx.app.exit();
        }
    }

    private void capture(String name) {
        Pixmap pixmap = Pixmap.createFromFrameBuffer(
                0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        Gdx.files.absolute(OUTPUT_DIR).mkdirs();
        // ⚠️ flipY=TRUE BẮT BUỘC — getFrameBufferPixmap trả dữ liệu THÔ của GL (dòng 0 = ĐÁY màn
        // hình), còn PNG quy ước dòng 0 = ĐỈNH. Bản 2-tham-số của writePNG mặc định flipY=false nên
        // ảnh ra bị LẬT DỌC. Đây chính là lỗi làm lượt chẩn đoán trước kết luận SAI ("nhân vật đứng
        // thẳng") trong khi trên màn hình nó đang lộn ngược — cùng loại bẫy đã ghi ở
        // RENDERING.md §11.1 (lần trước là script chụp không nhận biết DPI).
        PixmapIO.writePNG(Gdx.files.absolute(OUTPUT_DIR + name + ".png"), pixmap, Deflater.DEFAULT_COMPRESSION, true);
        pixmap.dispose();
        LOG.info("Đã chụp {}{}.png", OUTPUT_DIR, name);
    }

    @Override
    public void dispose() {
        if (models != null) {
            models.dispose();
        }
        if (shapeRenderer != null) {
            shapeRenderer.dispose();
        }
        if (skillVfx != null) {
            skillVfx.dispose();
        }
        if (floatingText != null) {
            floatingText.dispose(); // trước hud — floatingText mượn font của hud
        }
        if (hud != null) {
            hud.dispose();
        }
    }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Kage - Character Orientation Debug (cong cu chan doan, khong phai game)");
        config.setWindowedMode(900, 900);
        config.useVsync(false);
        new Lwjgl3Application(new CharacterOrientationDebugTool(), config);
    }
}

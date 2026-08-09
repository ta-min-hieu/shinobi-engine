package dev.kage.client.core.ui.hud;

import java.util.zip.Deflater;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.ScreenUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * THROWAWAY — xem trước bố cục {@link CombatHud} bằng ảnh chụp, KHÔNG cần chạy server/đăng nhập.
 * Dựng lên khi chỉnh HUD theo ảnh Ninja School Online chủ dự án gửi (2026-08-09): vòng lặp
 * "sửa → đăng nhập → nhìn" quá chậm để căn chỉnh giao diện.
 *
 * <p>Chạy: {@code ./gradlew :client:client-core:runHudPreview}. Xoá cùng công cụ chẩn đoán khác khi
 * HUD đã chốt.
 */
public final class HudPreviewTool extends ApplicationAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(HudPreviewTool.class);
    private static final String OUTPUT = "E:/study/libgdx/shinobi-engine/.debug-orientation/hud.png";

    private CombatHud hud;
    private int frame;

    @Override
    public void create() {
        hud = new CombatHud();
        hud.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        // Dữ liệu giả nhưng ĐÚNG dải giá trị thật (HP/Khí tối đa 100 — CombatConstants) để bố cục
        // phải chịu đúng độ dài chuỗi mà game thật sinh ra.
        hud.setLevel(12);
        hud.setPlayerHp(72, 100);
        hud.setPlayerChi(45, 100);
        // Vạch EXP (2026-08-09) — số thật của cấp 12 theo ExperienceCurve (100 × cấp = 1200).
        hud.setExperience(430L, 1200L);
        hud.setTarget("training_dummy", 38, 60);
        // Trạng thái SẴN SÀNG — đây mới là trạng thái người chơi thấy phần lớn thời gian; xem
        // trước ở trạng thái hồi chiêu (nút xám) sẽ đánh giá sai độ nổi bật của nút.
        hud.setSkillState("hud.skill.fireball", 0f);
        hud.setSkillReady(true);
    }

    @Override
    public void render() {
        // Nền xanh rêu giả lập màu map, để thấy HUD tương phản ra sao trên nền sáng chứ không phải
        // trên nền đen (nền đen làm panel tối trông "chìm" hơn thực tế).
        Gdx.gl.glClearColor(0.22f, 0.42f, 0.16f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        hud.render(1f / 60f);
        frame++;
        if (frame == 30) {
            Pixmap pixmap = ScreenUtils.getFrameBufferPixmap(
                    0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
            Gdx.files.absolute(OUTPUT).parent().mkdirs();
            // flipY=true BẮT BUỘC — xem WorldModelRenderer.Y_UP_TO_Z_UP về lần dính bẫy này.
            PixmapIO.writePNG(Gdx.files.absolute(OUTPUT), pixmap, Deflater.DEFAULT_COMPRESSION, true);
            pixmap.dispose();
            LOG.info("Đã chụp {}", OUTPUT);
            Gdx.app.exit();
        }
    }

    @Override
    public void dispose() {
        if (hud != null) {
            hud.dispose();
        }
    }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Kage - HUD preview (cong cu, khong phai game)");
        config.setWindowedMode(1280, 720); // đúng kích thước cửa sổ game thật (DesktopLauncher)
        config.useVsync(false);
        new Lwjgl3Application(new HudPreviewTool(), config);
    }
}

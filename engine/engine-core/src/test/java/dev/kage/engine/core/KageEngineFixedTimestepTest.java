package dev.kage.engine.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Vòng lặp fixed-timestep của {@link KageEngine} — thứ ENGINE.md §4 gọi là "quyết định thiết kế
 * quan trọng nhất của engine" và ADR-018 chốt ở 60Hz.
 *
 * <p>Bộ test đầu tiên của {@code engine-core}: cho tới 2026-08-30 module này <b>không có test
 * nào</b>, nên ô Exit Criteria "fixed timestep hoạt động" của Phase 1 chưa từng có bằng chứng nào
 * ngoài việc đọc code. Ba tính chất dưới đây là ba thứ hỏng âm thầm chứ không nổ: logic chạy nhanh
 * hơn/chậm hơn theo FPS máy (client và server lệch nhau dần), một frame chậm kéo theo hàng trăm
 * tick dồn ứ ("spiral of death"), và {@code alpha} nội suy ra ngoài [0,1) làm hình giật.
 *
 * <p>Không cần GL context: {@link KageEngine#render()} chỉ đọc {@code Gdx.graphics.getDeltaTime()},
 * nên một {@link Proxy} đóng vai {@link Graphics} là đủ. Đây cũng là lý do bộ test này khả thi mà
 * không kéo LWJGL vào — thứ từng làm cả module bị bỏ trống.
 */
class KageEngineFixedTimestepTest {

    /** 1/60s. Nhắc lại tại chỗ để test đọc được mà không phải suy từ hằng số đang kiểm. */
    private static final float FIXED_DT = 1f / 60f;

    /** Trần chống dồn tick của engine (`MAX_FRAME_TIME`), private nên chép lại đúng giá trị. */
    private static final float MAX_FRAME_TIME = 0.25f;

    private static final float TOLERANCE = 1e-6f;

    private static final int WINDOW_WIDTH = 1280;
    private static final int WINDOW_HEIGHT = 720;

    @AfterEach
    void clearGdxGraphics() {
        // Uỷ quyền cho một hàm static thay vì gán thẳng: SpotBugs (ST_WRITE_TO_STATIC_FROM_INSTANCE_
        // METHOD) chặn instance method ghi vào field static, và luật đó ĐÚNG — chỉ là ở đây thứ
        // đang ghi là biến toàn cục của libGDX, không phải trạng thái của lớp này.
        installGraphics(null);
    }

    /**
     * Cắm {@link Graphics} vào biến static toàn cục của libGDX.
     *
     * <p>{@code Gdx.graphics} là global thật: không dọn sau mỗi test thì lớp test khác chạy sau sẽ
     * dùng con proxy của lớp này mà không ai biết.
     */
    private static void installGraphics(Graphics graphics) {
        Gdx.graphics = graphics;
    }

    @Test
    void runsOneTickPerFixedDtInsideOneFrame() {
        RecordingScreen screen = new RecordingScreen();
        KageEngine engine = engineWith(screen);
        // 0,058s nằm giữa tick thứ 3 và thứ 4 (3×1/60 = 0,050; 4×1/60 = 0,067) — CỐ Ý không lấy
        // đúng bội số: `FIXED_DT` là float 0,016666668 (làm tròn LÊN), nên 0,05f thật ra nhỏ hơn
        // 3 lần trừ liên tiếp và chỉ ra 2 tick. Test bám ranh giới như thế là test đang đo lỗi làm
        // tròn của chính nó chứ không đo engine.
        renderFrames(engine, 0.058f);

        assertThat(screen.fixedUpdates).hasSize(3);
        assertThat(screen.fixedUpdates).allSatisfy(dt -> assertThat(dt).isEqualTo(FIXED_DT));
    }

    @Test
    void drawsEvenWhenNoTickIsDue() {
        RecordingScreen screen = new RecordingScreen();
        KageEngine engine = engineWith(screen);
        // Máy 100 FPS: mỗi frame ngắn hơn một tick logic.
        renderFrames(engine, 0.01f);

        assertThat(screen.fixedUpdates).isEmpty();
        assertThat(screen.renders).hasSize(1);
    }

    @Test
    void carriesTheLeftoverAcrossFrames() {
        RecordingScreen screen = new RecordingScreen();
        KageEngine engine = engineWith(screen);
        // Ba frame 0,01s = 0,03s: đủ cho ĐÚNG một tick, và phần dư phải sống qua ranh giới frame.
        // Không cộng dồn thì logic đứng yên vĩnh viễn ở mọi máy chạy nhanh hơn 60 FPS.
        renderFrames(engine, 0.01f, 0.01f, 0.01f);

        assertThat(screen.fixedUpdates).hasSize(1);
    }

    @Test
    void doesNotSpiralAfterOneVerySlowFrame() {
        RecordingScreen screen = new RecordingScreen();
        KageEngine engine = engineWith(screen);
        // GC pause / load asset 2 giây. Không chặn thì là 120 tick trong một frame, và mỗi tick lại
        // làm frame sau chậm hơn — vòng xoáy chết.
        renderFrames(engine, 2f);

        int ticksTheFrameWouldDemand = (int) (2f / FIXED_DT);
        int ceiling = (int) Math.ceil(MAX_FRAME_TIME / FIXED_DT);
        assertThat(screen.fixedUpdates).hasSizeLessThanOrEqualTo(ceiling);
        assertThat(screen.fixedUpdates).hasSizeLessThan(ticksTheFrameWouldDemand);
        // Và vẫn phải chạy: kẹp về 0 tick thì logic đứng hình mỗi lần máy khựng.
        assertThat(screen.fixedUpdates).isNotEmpty();
    }

    @Test
    void keepsLogicRateIndependentOfFrameRate() {
        // Chính là lời hứa của ADR-018: cùng một khoảng thời gian mô phỏng phải ra cùng số tick, dù
        // máy chạy 20 FPS hay 120 FPS. Lệch ở đây nghĩa là client và server trôi xa nhau dần.
        RecordingScreen slowScreen = new RecordingScreen();
        KageEngine slowEngine = engineWith(slowScreen);
        for (int frame = 0; frame < 20; frame++) {
            renderFrames(slowEngine, 1f / 20f);
        }

        RecordingScreen fastScreen = new RecordingScreen();
        KageEngine fastEngine = engineWith(fastScreen);
        for (int frame = 0; frame < 120; frame++) {
            renderFrames(fastEngine, 1f / 120f);
        }

        // Sai lệch cho phép đúng MỘT tick, và con số đó là đo được chứ không phải nới cho qua:
        // `FIXED_DT` = 1/60 làm tròn LÊN thành 0,016666668, nên 60 lần trừ tiêu tốn 1,00000008 giây
        // — nhiều hơn một giây mô phỏng cộng từ 20 frame (1,0000000149). Máy 20 FPS vì thế ra 59
        // tick, máy 120 FPS ra 60. Trôi 1 tick/giây là 0,0001% và không tích luỹ thành lệch pha vì
        // server chốt trạng thái; trôi HAI tick trở lên thì mới là hỏng.
        assertThat(slowScreen.fixedUpdates.size()).isCloseTo(60, Offset.offset(1));
        assertThat(fastScreen.fixedUpdates.size()).isCloseTo(60, Offset.offset(1));
        assertThat(Math.abs(slowScreen.fixedUpdates.size() - fastScreen.fixedUpdates.size()))
                .isLessThanOrEqualTo(1);
    }

    @Test
    void passesTheLeftoverToRenderAsAlpha() {
        RecordingScreen screen = new RecordingScreen();
        KageEngine engine = engineWith(screen);
        // 0,02s = 1 tick + dư 0,0033s ⇒ alpha = dư / FIXED_DT = 0,2.
        renderFrames(engine, 0.02f);

        assertThat(screen.renders).hasSize(1);
        assertThat(screen.renders.get(0)).isCloseTo(0.2f, Offset.offset(1e-4f));
    }

    @Test
    void neverPassesAnAlphaOutsideZeroToOne() {
        RecordingScreen screen = new RecordingScreen();
        KageEngine engine = engineWith(screen);
        // Các frame lẻ cố ý không chia hết cho 1/60 — alpha là phần dư nên phải luôn < 1, nếu không
        // lớp nội suy hình sẽ vẽ vượt qua tick kế tiếp.
        renderFrames(engine, 0.007f, 0.013f, 0.031f, 0.004f, 0.019f);

        assertThat(screen.renders).hasSize(5);
        assertThat(screen.renders)
                .allSatisfy(
                        alpha -> assertThat(alpha).isGreaterThanOrEqualTo(0f).isLessThan(1f + TOLERANCE));
    }

    @Test
    void handsTheNewScreenTheWindowSizeWhenSwitching() {
        // Lỗi thật 2026-08-08: setScreen() không gọi resize(), nên màn hình vào bằng cách chuyển
        // màn (Login → World) có ma trận chiếu suy biến về 0 và không vẽ được gì. libGDX chỉ tự
        // resize lúc khởi động app và lúc cửa sổ đổi kích thước thật.
        RecordingScreen first = new RecordingScreen();
        KageEngine engine = engineWith(first);
        RecordingScreen second = new RecordingScreen();
        engine.setScreen(second);

        assertThat(second.shown).isTrue();
        assertThat(second.resizes).containsExactly(WINDOW_WIDTH + "x" + WINDOW_HEIGHT);
    }

    @Test
    void disposesTheOldScreenWhenSwitching() {
        // Login/CharacterSelect mỗi màn tự sở hữu Stage + SpriteBatch + font; không có stack nào
        // giữ lại để dispose sau, nên không dispose ở đây là rò rỉ native mỗi lần chuyển màn.
        RecordingScreen first = new RecordingScreen();
        KageEngine engine = engineWith(first);
        engine.setScreen(new RecordingScreen());

        assertThat(first.hidden).isTrue();
        assertThat(first.disposed).isTrue();
    }

    /** Dựng engine đã {@code create()} xong với màn hình cho trước, và cắm {@link Graphics} giả. */
    private static KageEngine engineWith(GameScreen screen) {
        installGraphics(fakeGraphics(0f));
        KageEngine engine = new KageEngine(new StubPlatform(), givenEngine -> screen);
        engine.create();
        return engine;
    }

    /** Chạy từng frame với đúng delta của nó — mỗi frame là một lần đổi {@code Gdx.graphics}. */
    private static void renderFrames(KageEngine engine, float... deltas) {
        for (float delta : deltas) {
            installGraphics(fakeGraphics(delta));
            engine.render();
        }
    }

    /**
     * {@link Graphics} là interface hơn 40 phương thức và engine chỉ gọi ba cái. Proxy thay vì một
     * lớp giả viết tay: lớp viết tay phải cập nhật mỗi lần libGDX thêm phương thức mới, còn thứ
     * test này nói về chỉ là {@code getDeltaTime}.
     */
    private static Graphics fakeGraphics(float deltaTime) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "getDeltaTime", "getRawDeltaTime" -> deltaTime;
            case "getWidth", "getBackBufferWidth" -> WINDOW_WIDTH;
            case "getHeight", "getBackBufferHeight" -> WINDOW_HEIGHT;
            default -> defaultValueFor(method.getReturnType());
        };
        return (Graphics)
                Proxy.newProxyInstance(Graphics.class.getClassLoader(), new Class<?>[] {Graphics.class}, handler);
    }

    private static Object defaultValueFor(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == float.class) {
            return 0f;
        }
        if (returnType == long.class) {
            return 0L;
        }
        // int/short/byte/char/void: 0 là giá trị hợp lệ cho cả nhóm, void thì không ai đọc.
        return 0;
    }

    /** Ghi lại đúng những gì engine gọi, không làm gì khác — không có GL trong test này. */
    private static final class RecordingScreen implements GameScreen {

        private final List<Float> fixedUpdates = new ArrayList<>();
        private final List<Float> renders = new ArrayList<>();
        private final List<String> resizes = new ArrayList<>();
        private boolean shown;
        private boolean hidden;
        private boolean disposed;

        @Override
        public void show() {
            shown = true;
        }

        @Override
        public void hide() {
            hidden = true;
        }

        @Override
        public void resize(int width, int height) {
            resizes.add(width + "x" + height);
        }

        @Override
        public void fixedUpdate(float fixedDeltaTime) {
            fixedUpdates.add(fixedDeltaTime);
        }

        @Override
        public void render(float alpha) {
            renders.add(alpha);
        }

        @Override
        public void dispose() {
            disposed = true;
        }
    }

    /** {@link PlatformAdapter} chỉ để dựng engine — không test nào dưới đây chạm tới nó. */
    private static final class StubPlatform implements PlatformAdapter {

        @Override
        public Path savePath() {
            return Path.of(".");
        }

        @Override
        public InputType inputType() {
            return InputType.KEYBOARD_MOUSE;
        }

        @Override
        public float uiScale() {
            return 1f;
        }

        @Override
        public void openUrl(String url) {
            // Không dùng trong test vòng lặp.
        }

        @Override
        public void vibrate(int millis) {
            // Không dùng trong test vòng lặp.
        }
    }
}

package dev.kage.engine.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.kage.engine.ecs.EcsWorld;
import dev.kage.engine.ecs.Family;
import dev.kage.engine.ecs.IteratingKSystem;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;

/**
 * Bộ kiểm THUỘC TÍNH cho vòng lặp của {@link KageEngine} (2026-10-10): chuỗi thời gian khung ngẫu
 * nhiên có seed — khung bình thường, khung khựng nhiều giây, khung 0 giây, và thời gian khung HỎNG
 * ({@code NaN}, âm, vô cực) — xen kẽ việc đổi màn hình ngay giữa {@code fixedUpdate}. Sau MỖI khung:
 *
 * <ul>
 *   <li>{@code alpha} giao cho {@code render} hữu hạn và nằm trong {@code [0, 1]};
 *   <li>một khung không chạy quá trần chống dồn tick;
 *   <li>mỗi tick, {@link EcsWorld} được update đúng một lần, SAU {@code fixedUpdate} của màn hình;
 *   <li>màn hình đã bị thay không còn nhận lời gọi nào ngoài {@code hide}/{@code dispose};
 *   <li>tổng số tick khớp tổng thời gian khung hợp lệ (đã kẹp trần), lệch không quá 0,1% + 1 tick;
 *   <li>sau một thời gian khung hỏng, vòng lặp vẫn chạy tiếp ở khung tử tế kế tiếp.
 * </ul>
 *
 * <p>Chạy lại một seed đỏ: sửa tạm {@link #BASE_SEED}.
 */
class KageEngineFrameTimePropertyTest {

    private static final long BASE_SEED = 0xC0FEL;
    private static final int SEEDS = 200;
    private static final int FRAMES_PER_SEED = 600;
    private static final float FIXED_DT = 1f / 60f;
    private static final float MAX_FRAME_TIME = 0.25f;
    private static final int MAX_TICKS_PER_FRAME = (int) Math.ceil(MAX_FRAME_TIME / FIXED_DT) + 1;

    /** Lời gọi engine gửi xuống, theo đúng thứ tự: màn hình và {@link EcsWorld} ghi chung một sổ. */
    private final List<String> calls = new ArrayList<>();

    private final List<Float> alphas = new ArrayList<>();
    private KageEngine engine;
    private int screensCreated;
    private int switchAtFixedUpdate;

    private final class ScriptedScreen implements GameScreen {
        private final String name = "screen" + screensCreated++;
        private boolean disposed;

        @Override
        public void fixedUpdate(float fixedDeltaTime) {
            calls.add(disposed ? "DEAD:" + name + ":tick" : "tick");
            switchAtFixedUpdate--;
            if (switchAtFixedUpdate == 0) {
                engine.setScreen(new ScriptedScreen());
            }
        }

        @Override
        public void render(float alpha) {
            calls.add(disposed ? "DEAD:" + name + ":render" : "render");
            alphas.add(alpha);
        }

        @Override
        public void dispose() {
            disposed = true;
        }
    }

    @AfterEach
    void clearGdxGraphics() {
        installGraphics(0f);
        clearGraphics();
    }

    @Test
    @Timeout(60)
    void shouldKeepLoopInvariants_underArbitraryFrameTimes() {
        int brokenFrames = 0;
        for (int i = 0; i < SEEDS; i++) {
            long seed = BASE_SEED + i;
            SplittableRandom random = new SplittableRandom(seed);
            startEngine();
            double validSeconds = 0.0;
            int ticks = 0;
            for (int frame = 0; frame < FRAMES_PER_SEED; frame++) {
                float delta = frameTime(random);
                boolean broken = !(delta >= 0f);
                brokenFrames += broken ? 1 : 0;
                validSeconds += broken ? 0.0 : Math.min(delta, MAX_FRAME_TIME);
                if (random.nextInt(50) == 0) {
                    switchAtFixedUpdate = 1 + random.nextInt(4);
                }

                int ticksThisFrame = renderFrame(delta, "seed=" + seed + " frame=" + frame + " delta=" + delta);

                ticks += ticksThisFrame;
            }
            double expectedTicks = validSeconds / FIXED_DT;
            assertThat((double) ticks)
                    .as("tổng tick, seed=" + seed + " (thời gian hợp lệ " + validSeconds + "s)")
                    .isBetween(expectedTicks * 0.999 - 1.0, expectedTicks * 1.001 + 1.0);
        }
        assertThat(brokenFrames).as("phải có thời gian khung hỏng").isPositive();
    }

    /** Ca tối giản của lỗi: một {@code NaN} duy nhất từng đầu độc accumulator vĩnh viễn. */
    @Test
    void shouldKeepTicking_afterASingleNotANumberFrameTime() {
        startEngine();
        renderFrame(Float.NaN, "NaN");

        int ticks = 0;
        for (int frame = 0; frame < 60; frame++) {
            ticks += renderFrame(1f / 60f, "khung thường " + frame);
        }

        assertThat(ticks).isBetween(58, 60);
    }

    @Test
    void shouldNotOweTicks_afterANegativeFrameTime() {
        startEngine();
        renderFrame(-5f, "âm");

        int ticks = 0;
        for (int frame = 0; frame < 60; frame++) {
            ticks += renderFrame(1f / 60f, "khung thường " + frame);
        }

        assertThat(ticks).isBetween(58, 60);
    }

    private void startEngine() {
        calls.clear();
        alphas.clear();
        switchAtFixedUpdate = 0;
        installGraphics(0f);
        engine = new KageEngine(new StubPlatform(), givenEngine -> new ScriptedScreen());
        EcsWorld world = engine.services().get(EcsWorld.class);
        world.addSystem(new IteratingKSystem(Family.all(PositionComponent.class).get()) {
            @Override
            protected void processEntity(KEntity entity, float deltaTime) {
                calls.add("ecs");
            }
        });
        world.createEntity().add(new PositionComponent());
        engine.create();
    }

    /** Chạy một khung, kiểm bất biến của khung đó, trả số tick đã chạy. */
    private int renderFrame(float delta, String description) {
        calls.clear();
        alphas.clear();
        installGraphics(delta);

        engine.render();

        int ticks = calls.size() / 2;
        List<String> expected = new ArrayList<>();
        for (int tick = 0; tick < ticks; tick++) {
            expected.add("tick");
            expected.add("ecs");
        }
        expected.add("render");
        assertThat(calls).as("thứ tự lời gọi trong khung: " + description).isEqualTo(expected);
        assertThat(ticks).as("số tick trong khung: " + description).isLessThanOrEqualTo(MAX_TICKS_PER_FRAME);
        assertThat(alphas).as("alpha: " + description).hasSize(1);
        assertThat(alphas.get(0)).as("alpha: " + description).isBetween(0f, 1f);
        return ticks;
    }

    private static float frameTime(SplittableRandom random) {
        return switch (random.nextInt(40)) {
            case 0 -> Float.NaN;
            case 1 -> -random.nextFloat() * 3f;
            case 2 -> Float.POSITIVE_INFINITY;
            case 3 -> Float.NEGATIVE_INFINITY;
            case 4 -> 0f;
            case 5 -> 0.3f + random.nextFloat() * 4f;
            case 6 -> Float.MIN_VALUE;
            default -> 0.002f + random.nextFloat() * 0.04f;
        };
    }

    private static void installGraphics(float deltaTime) {
        InvocationHandler handler = (proxy, method, args) -> {
            Class<?> type = method.getReturnType();
            if (type == float.class) {
                return deltaTime;
            }
            if (type == int.class) {
                return 720;
            }
            if (type == boolean.class) {
                return false;
            }
            return type == long.class ? (Object) 0L : null;
        };
        Gdx.graphics = (Graphics)
                Proxy.newProxyInstance(Graphics.class.getClassLoader(), new Class<?>[] {Graphics.class}, handler);
    }

    private static void clearGraphics() {
        Gdx.graphics = null;
    }

    /** {@link PlatformAdapter} chỉ để dựng engine. */
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

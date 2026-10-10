package dev.kage.engine.core;

import java.util.function.Function;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;

import dev.kage.engine.ecs.EcsWorld;
import dev.kage.engine.ecs.EventBus;

/**
 * Composition root của engine (ENGINE.md §3, §5). Vòng lặp fixed-timestep — quyết định thiết kế
 * quan trọng nhất của engine (ENGINE.md §4): logic luôn chạy đúng {@code FIXED_DT} bất kể FPS máy,
 * client/server đồng bộ tick rate.
 *
 * <p>{@code MAX_FRAME_TIME} chống "spiral of death": một frame chậm (GC pause, load asset) không
 * được dồn accumulator tới mức chạy hàng chục tick liên tiếp rồi treo hẳn.
 */
public final class KageEngine extends ApplicationAdapter {

    /** Tick rate 60Hz — ADR-018. Client và server PHẢI dùng cùng giá trị này. */
    public static final float TICK_RATE = 60f;

    public static final float FIXED_DT = 1f / TICK_RATE;
    private static final float MAX_FRAME_TIME = 0.25f;

    private final ServiceRegistry services = new ServiceRegistry();
    private final EcsWorld ecsWorld = new EcsWorld();
    private final EventBus eventBus = new EventBus();
    private final Function<KageEngine, GameScreen> initialScreenFactory;

    private float accumulator;
    private GameScreen currentScreen;

    /**
     * @param initialScreenFactory tạo màn hình đầu tiên. Nhận vào lúc {@link #create()} (KHÔNG
     *     phải constructor) vì nhiều thứ trong Screen (VD ShapeRenderer, SpriteBatch) cần GL
     *     context đã sẵn sàng — libGDX chỉ đảm bảo điều đó từ {@code create()} trở đi.
     */
    public KageEngine(PlatformAdapter platform, Function<KageEngine, GameScreen> initialScreenFactory) {
        // Thứ tự đăng ký KHÔNG tuỳ tiện — ResourceManager sẽ chèn vào đây khi có ở bước sau
        // (ENGINE.md §5). Service lấy ở composition root, KHÔNG truyền ServiceRegistry cho System.
        services.register(EcsWorld.class, ecsWorld);
        services.register(EventBus.class, eventBus);
        services.register(PlatformAdapter.class, platform);
        this.initialScreenFactory = initialScreenFactory;
    }

    public ServiceRegistry services() {
        return services;
    }

    public void setScreen(GameScreen screen) {
        if (currentScreen != null) {
            currentScreen.hide();
            // Dispose NGAY màn hình cũ, không đợi tới lúc thoát app — Login/CharacterSelect (mỗi
            // cái tự sở hữu Stage+SpriteBatch+font riêng, xem client-core/screen/) chỉ sống một lần
            // rồi bị thay bởi màn kế tiếp, không có registry/stack nào giữ lại để dispose sau
            // (GameScreen javadoc: "Splash → Loading → Login → ... → InGame"). Không gọi ở đây thì
            // texture/font native của màn cũ rò rỉ mỗi lần chuyển màn hình.
            currentScreen.dispose();
        }
        currentScreen = screen;
        currentScreen.show();
        // libGDX chỉ gọi resize() của ApplicationListener 1 lần lúc app khởi động thật (khi màn hình
        // ĐẦU TIÊN đang là currentScreen) và mỗi lần cửa sổ đổi kích thước thật — chuyển màn hình
        // giữa chừng qua setScreen() (Login → CharacterSelect → World) không phải resize cửa sổ nên
        // KHÔNG tự kích hoạt lại. Màn hình mới do đó không bao giờ nhận đúng kích thước nếu không
        // gọi tay ở đây — Stage/ScreenViewport (Login/CharacterSelect/HUD) tự áp lại viewport mỗi
        // frame nên "chịu được", còn FitViewport của WorldScreen thì không: camera.viewportWidth/
        // Height ở trạng thái mặc định (0) cho tới lần resize() đầu tiên, khiến ma trận chiếu suy
        // biến về 0 và không có gì render được — phát hiện thật bằng log, không suy luận tĩnh.
        currentScreen.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    @Override
    public void create() {
        setScreen(initialScreenFactory.apply(this));
    }

    @Override
    public void render() {
        accumulator += usableFrameTime(Gdx.graphics.getDeltaTime());

        while (accumulator >= FIXED_DT) {
            if (currentScreen != null) {
                currentScreen.fixedUpdate(FIXED_DT);
            }
            ecsWorld.update(FIXED_DT);
            accumulator -= FIXED_DT;
        }

        float alpha = accumulator / FIXED_DT;
        if (currentScreen != null) {
            currentScreen.render(alpha);
        }
    }

    /**
     * Phần thời gian khung được đưa vào accumulator: kẹp trần {@code MAX_FRAME_TIME}, và coi thời
     * gian khung hỏng ({@code NaN}, âm) là 0. Không lọc thì MỘT giá trị {@code NaN} đầu độc
     * accumulator vĩnh viễn — {@code NaN >= FIXED_DT} luôn sai nên logic đứng hình mãi mãi mà không
     * báo gì — còn một giá trị âm bắt game "trả nợ" đủ ngần ấy giây rồi mới tick lại, và đưa
     * {@code alpha} âm xuống lớp nội suy hình. Backend LWJGL3 tính delta từ {@code System.nanoTime()}
     * nên hiện không sinh ra giá trị như vậy; hàm này để vòng lặp không phụ thuộc vào điều đó.
     */
    private static float usableFrameTime(float frameTime) {
        if (Float.isNaN(frameTime) || frameTime <= 0f) {
            return 0f;
        }
        return Math.min(frameTime, MAX_FRAME_TIME);
    }

    @Override
    public void resize(int width, int height) {
        if (currentScreen != null) {
            currentScreen.resize(width, height);
        }
    }

    @Override
    public void pause() {
        if (currentScreen != null) {
            currentScreen.pause();
        }
    }

    @Override
    public void resume() {
        if (currentScreen != null) {
            currentScreen.resume();
        }
    }

    @Override
    public void dispose() {
        if (currentScreen != null) {
            currentScreen.hide();
            currentScreen.dispose();
        }
    }
}

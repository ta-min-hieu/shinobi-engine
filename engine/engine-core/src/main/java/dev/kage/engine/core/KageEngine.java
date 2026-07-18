package dev.kage.engine.core;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import dev.kage.engine.ecs.EcsWorld;
import dev.kage.engine.ecs.EventBus;
import java.util.function.Function;

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
        }
        currentScreen = screen;
        currentScreen.show();
    }

    @Override
    public void create() {
        setScreen(initialScreenFactory.apply(this));
    }

    @Override
    public void render() {
        float frameTime = Gdx.graphics.getDeltaTime();
        accumulator += Math.min(frameTime, MAX_FRAME_TIME);

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

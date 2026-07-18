package dev.kage.client.core.playground;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import dev.kage.client.core.input.GameAction;
import dev.kage.client.core.input.InputMapper;
import dev.kage.engine.core.GameScreen;
import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.EcsWorld;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.engine.ecs.component.VelocityComponent;
import dev.kage.engine.ecs.system.MovementSystem;

/**
 * Màn hình demo Phase 1 — verify fixed timestep 60Hz + input abstraction + ECS hoạt động
 * (ROADMAP.md Phase 1 Exit Criteria). KHÔNG phải gameplay thật — không có asset pipeline
 * (RESOURCE.md), không có combat/skill. Vẽ 1 hình vuông màu di chuyển bằng {@link ShapeRenderer}.
 *
 * <p>⚠️ Dùng {@link com.badlogic.gdx.utils.viewport.FitViewport} tạm thời — ADR-020 (Viewport
 * strategy, ảnh hưởng công bằng PvP) chưa chốt (RENDERING.md §3).
 */
public final class PlaygroundScreen implements GameScreen {

    private static final ComponentMapper<PositionComponent> POSITION =
            ComponentMapper.getFor(PositionComponent.class);
    private static final ComponentMapper<VelocityComponent> VELOCITY =
            ComponentMapper.getFor(VelocityComponent.class);

    private final InputMapper inputMapper;
    private final KEntity player;

    private final OrthographicCamera camera = new OrthographicCamera();
    private final Viewport viewport;
    private final ShapeRenderer shapeRenderer = new ShapeRenderer();

    public PlaygroundScreen(EcsWorld world, InputMapper inputMapper) {
        this.inputMapper = inputMapper;
        this.viewport = new FitViewport(
                PlaygroundConstants.VIEWPORT_HEIGHT_UNITS, PlaygroundConstants.VIEWPORT_HEIGHT_UNITS, camera);

        world.addSystem(new MovementSystem());

        this.player = world.createEntity();
        var pos = new PositionComponent();
        pos.x = 0f;
        pos.y = 0f;
        player.add(pos);
        player.add(new VelocityComponent());
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void fixedUpdate(float fixedDeltaTime) {
        // Input -> Ý ĐỊNH -> VelocityComponent. Gameplay không biết phím nào được bấm (CLIENT.md §5).
        float vx = 0f;
        float vy = 0f;
        if (inputMapper.isPressed(GameAction.MOVE_LEFT)) {
            vx -= PlaygroundConstants.MOVE_SPEED;
        }
        if (inputMapper.isPressed(GameAction.MOVE_RIGHT)) {
            vx += PlaygroundConstants.MOVE_SPEED;
        }
        if (inputMapper.isPressed(GameAction.MOVE_DOWN)) {
            vy -= PlaygroundConstants.MOVE_SPEED;
        }
        if (inputMapper.isPressed(GameAction.MOVE_UP)) {
            vy += PlaygroundConstants.MOVE_SPEED;
        }

        var velocity = VELOCITY.get(player);
        velocity.x = vx;
        velocity.y = vy;

        // world.update() gọi ở KageEngine (chung cho mọi System) — MovementSystem đã add ở đây
        // sẽ được Ashley engine chạy khi KageEngine.render() gọi world.update(FIXED_DT).
    }

    @Override
    public void render(float alpha) {
        var pos = POSITION.get(player);
        // Nội suy giữa vị trí tick trước và hiện tại để hình mượt dù logic chỉ update 60 lần/giây
        // (RENDERING.md §7) — tách biệt khỏi FPS thật của máy.
        float renderX = pos.prevX + (pos.x - pos.prevX) * alpha;
        float renderY = pos.prevY + (pos.y - pos.prevY) * alpha;

        Gdx.gl.glClearColor(0.1f, 0.1f, 0.15f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        camera.update();
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(Color.CORAL);
        shapeRenderer.rect(
                renderX - PlaygroundConstants.BOX_SIZE / 2f,
                renderY - PlaygroundConstants.BOX_SIZE / 2f,
                PlaygroundConstants.BOX_SIZE,
                PlaygroundConstants.BOX_SIZE);
        shapeRenderer.end();
    }

    @Override
    public void dispose() {
        shapeRenderer.dispose();
    }
}

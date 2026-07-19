package dev.kage.server.world;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.EcsWorld;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.engine.ecs.component.VelocityComponent;
import dev.kage.engine.ecs.system.MovementSystem;
import dev.kage.server.world.component.CharacterIdComponent;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.LockSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Composition root của server-world (SERVER.md §1-3) — sở hữu {@link EcsWorld} + vòng lặp tick
 * single-thread riêng (Platform Thread, KHÔNG Virtual Thread — /CLAUDE.md §6). Đây là API DUY
 * NHẤT server-gateway được gọi vào server-world (module này không biết Netty/DB).
 *
 * <p>{@link #attachCharacter}, {@link #detachCharacter}, {@link #enqueueMoveCommand} được gọi từ
 * Netty EventLoop hoặc Virtual Thread của gateway — CHỈ enqueue (rẻ, thread-safe), không bao giờ
 * chạm {@link EcsWorld} trực tiếp (SERVER.md §2: "Virtual Thread cấm sửa ECS state trực tiếp").
 * Việc thật xảy ra ở đầu tick kế tiếp trên {@link #tickLoop}, đúng pseudocode SERVER.md §3.
 */
public final class WorldFacade {

    private static final Logger LOG = LoggerFactory.getLogger(WorldFacade.class);

    private static final ComponentMapper<PositionComponent> POSITION =
            ComponentMapper.getFor(PositionComponent.class);
    private static final ComponentMapper<VelocityComponent> VELOCITY =
            ComponentMapper.getFor(VelocityComponent.class);

    private final WorldOutboundSink outboundSink;
    private final Queue<WorldCommand> inbox = new ConcurrentLinkedQueue<>();
    private final EcsWorld ecsWorld = new EcsWorld();
    private final AreaOfInterestTracker aoiTracker;

    // Chỉ tick thread được đọc/ghi map này (không đồng bộ hoá) — đúng ranh giới "ECS state chỉ
    // sửa trên tick thread" (SERVER.md §2).
    private final Map<Long, KEntity> entitiesByCharacterId = new HashMap<>();

    private volatile boolean running;
    private Thread tickThread;

    public WorldFacade(WorldOutboundSink outboundSink) {
        this.outboundSink = outboundSink;
        this.aoiTracker = new AreaOfInterestTracker(outboundSink);
        ecsWorld.addSystem(new MovementSystem());
    }

    /** Bắt đầu tick loop trên thread riêng — gọi 1 lần lúc khởi động server. */
    public void start() {
        running = true;
        tickThread = new Thread(this::tickLoop, "world-tick");
        tickThread.start();
    }

    /** Dừng tick loop (SERVER.md §10 — graceful shutdown, không {@code kill -9}). */
    public void stop() {
        running = false;
        if (tickThread != null) {
            tickThread.interrupt();
            try {
                tickThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void attachCharacter(long characterId) {
        inbox.add(new WorldCommand.AttachCharacterCommand(characterId));
    }

    public void detachCharacter(long characterId) {
        inbox.add(new WorldCommand.DetachCharacterCommand(characterId));
    }

    public void enqueueMoveCommand(long characterId, int sequenceId, float dirX, float dirY) {
        inbox.add(new WorldCommand.MoveIntentCommand(characterId, sequenceId, dirX, dirY));
    }

    private void tickLoop() {
        long fixedDtNanos = (long) (WorldConstants.FIXED_DT * 1_000_000_000L);
        while (running) {
            long start = System.nanoTime();

            drainInbox();
            ecsWorld.update(WorldConstants.FIXED_DT);
            aoiTracker.sync(entitiesByCharacterId);

            long elapsed = System.nanoTime() - start;
            long sleepNanos = fixedDtNanos - elapsed;
            if (sleepNanos > 0) {
                LockSupport.parkNanos(sleepNanos);
            } else {
                LOG.warn("World tick quá hạn: {}ms", elapsed / 1_000_000);
            }
        }
    }

    private void drainInbox() {
        WorldCommand command;
        while ((command = inbox.poll()) != null) {
            switch (command) {
                case WorldCommand.AttachCharacterCommand c -> handleAttach(c.characterId());
                case WorldCommand.DetachCharacterCommand c -> handleDetach(c.characterId());
                case WorldCommand.MoveIntentCommand c -> handleMoveIntent(c);
            }
        }
    }

    private void handleAttach(long characterId) {
        if (entitiesByCharacterId.containsKey(characterId)) {
            return; // đã attach — bỏ qua an toàn thay vì tạo entity trùng.
        }
        var entity = ecsWorld.createEntity();

        var position = new PositionComponent();
        position.x = WorldConstants.SPAWN_X;
        position.y = WorldConstants.SPAWN_Y;
        position.prevX = position.x;
        position.prevY = position.y;
        entity.add(position);
        entity.add(new VelocityComponent());

        var characterIdComponent = new CharacterIdComponent();
        characterIdComponent.characterId = characterId;
        entity.add(characterIdComponent);

        entitiesByCharacterId.put(characterId, entity);
        aoiTracker.onAttach(characterId);
    }

    private void handleDetach(long characterId) {
        var entity = entitiesByCharacterId.remove(characterId);
        if (entity == null) {
            return;
        }
        aoiTracker.onDetach(characterId);
        ecsWorld.removeEntity(entity);
    }

    private void handleMoveIntent(WorldCommand.MoveIntentCommand intent) {
        var entity = entitiesByCharacterId.get(intent.characterId());
        if (entity == null) {
            // Race hiếm: MoveCommand tới trước khi AttachCharacterCommand cùng batch được xử lý
            // xong — bỏ qua an toàn, client sẽ gửi lại lệnh tiếp theo khi hướng đổi lần sau.
            return;
        }

        float[] direction = clampDirection(intent.dirX(), intent.dirY());
        var velocity = VELOCITY.get(entity);
        velocity.x = direction[0] * WorldConstants.MOVE_SPEED_MPS;
        velocity.y = direction[1] * WorldConstants.MOVE_SPEED_MPS;

        var position = POSITION.get(entity);
        outboundSink.onMoveAccepted(intent.characterId(), intent.sequenceId(), position.x, position.y);
    }

    /** Server SỞ HỮU tốc độ — client chỉ gửi hướng, clamp về unit vector chặn speed-hack. */
    private static float[] clampDirection(float dirX, float dirY) {
        float lengthSquared = (dirX * dirX) + (dirY * dirY);
        if (lengthSquared > 1f) {
            float length = (float) Math.sqrt(lengthSquared);
            return new float[] {dirX / length, dirY / length};
        }
        return new float[] {dirX, dirY};
    }
}

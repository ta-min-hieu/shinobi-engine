package dev.kage.server.gateway;

import dev.kage.character.domain.PositionSnapshot;
import dev.kage.inventory.api.InventoryFacade;
import dev.kage.protocol.AttackResultEvent;
import dev.kage.protocol.CharacterDespawnedEvent;
import dev.kage.protocol.CharacterMovedEvent;
import dev.kage.protocol.CharacterSpawnedEvent;
import dev.kage.protocol.ItemGrantedEvent;
import dev.kage.protocol.MonsterDespawnedEvent;
import dev.kage.protocol.MonsterMovedEvent;
import dev.kage.protocol.MonsterSpawnedEvent;
import dev.kage.protocol.MoveResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.ServerShuttingDownEvent;
import dev.kage.protocol.SkillResultEvent;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;
import dev.kage.server.world.WorldOutboundSink;
import io.netty.channel.ChannelHandlerContext;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sổ đăng ký {@code characterId -> ChannelHandlerContext} + implementation THẬT của
 * {@link WorldOutboundSink} (SERVER.md §2: "Response/Event vào outbound queue"). MODULE DUY
 * NHẤT được import Netty (Điều 23) nên đây là chỗ duy nhất server-world's kết quả tick được biến
 * thành packet + ghi ra dây — server-world tự nó không biết {@code ChannelHandlerContext}.
 *
 * <p>Mọi phương thức {@code onXxx} bị gọi TRÊN world tick thread ({@link WorldOutboundSink}
 * javadoc) — {@code ctx.writeAndFlush} an toàn gọi từ thread khác EventLoop (Netty tự chuyển
 * task nội bộ), nên KHÔNG có blocking nào ở đây.
 */
final class ConnectedCharacterRegistry implements WorldOutboundSink {

    private final InventoryFacade inventoryFacade;
    private final Map<Long, ChannelHandlerContext> contextsByCharacterId = new ConcurrentHashMap<>();
    // Vị trí MỚI NHẤT của CHÍNH nhân vật (không phải nhân vật khác) — nguồn cho
    // CharacterPositionPersister lưu định kỳ + lúc logout (SERVER.md §8), cập nhật mỗi lần
    // onMoveAccepted (server-world xác nhận lệnh di chuyển của connection này).
    private final Map<Long, PositionSnapshot> lastKnownPosition = new ConcurrentHashMap<>();
    // onMonsterKilled bị gọi TRÊN world tick thread (WorldOutboundSink javadoc) — grantLoot chạm
    // DB (blocking), CẤM chạy trực tiếp ở đó (server/CLAUDE.md: Game Tick cấm blocking I/O). Đẩy
    // sang Virtual Thread riêng, cùng pattern authExecutor của GatewayChannelHandler.
    private final ExecutorService lootExecutor = Executors.newVirtualThreadPerTaskExecutor();

    ConnectedCharacterRegistry(InventoryFacade inventoryFacade) {
        this.inventoryFacade = inventoryFacade;
    }

    void register(long characterId, ChannelHandlerContext ctx) {
        contextsByCharacterId.put(characterId, ctx);
    }

    void unregister(long characterId) {
        contextsByCharacterId.remove(characterId);
        lastKnownPosition.remove(characterId);
    }

    /** Bản chụp vị trí đã biết của MỌI nhân vật đang kết nối — dùng cho lưu định kỳ (quét hết). */
    Map<Long, PositionSnapshot> snapshotPositions() {
        return Map.copyOf(lastKnownPosition);
    }

    /** Vị trí đã biết của MỘT nhân vật, hoặc {@code null} nếu chưa từng di chuyển kể từ khi vào world. */
    PositionSnapshot lastKnownPosition(long characterId) {
        return lastKnownPosition.get(characterId);
    }

    /** SERVER.md §10 bước 2 — báo MỌI connection đã chọn nhân vật TRƯỚC khi đóng kết nối. */
    void broadcastShuttingDown() {
        var event = ServerShuttingDownEvent.newBuilder().build();
        byte[] payload = event.toByteArray();
        contextsByCharacterId.values()
                .forEach(ctx -> send(ctx, Opcode.OPCODE_SERVER_SHUTTING_DOWN_EVENT_VALUE, 0, payload));
    }

    @Override
    public void onMoveAccepted(long characterId, int sequenceId, float x, float y) {
        lastKnownPosition.put(characterId, new PositionSnapshot(x, y));
        var ctx = contextsByCharacterId.get(characterId);
        if (ctx == null) {
            return; // đã ngắt kết nối giữa lúc lệnh còn trong hàng đợi world — bỏ qua an toàn.
        }
        var response = MoveResponse.newBuilder().setX(x).setY(y).build();
        send(ctx, Opcode.OPCODE_MOVE_RESPONSE_VALUE, sequenceId, response.toByteArray());
    }

    @Override
    public void onCharacterSpawned(long targetCharacterId, long spawnedCharacterId, float x, float y) {
        var ctx = contextsByCharacterId.get(targetCharacterId);
        if (ctx == null) {
            return;
        }
        var event = CharacterSpawnedEvent.newBuilder()
                .setCharacterId(spawnedCharacterId)
                .setX(x)
                .setY(y)
                .build();
        send(ctx, Opcode.OPCODE_CHARACTER_SPAWNED_EVENT_VALUE, 0, event.toByteArray());
    }

    @Override
    public void onCharacterDespawned(long targetCharacterId, long despawnedCharacterId) {
        var ctx = contextsByCharacterId.get(targetCharacterId);
        if (ctx == null) {
            return;
        }
        var event = CharacterDespawnedEvent.newBuilder()
                .setCharacterId(despawnedCharacterId)
                .build();
        send(ctx, Opcode.OPCODE_CHARACTER_DESPAWNED_EVENT_VALUE, 0, event.toByteArray());
    }

    @Override
    public void onCharacterMoved(long targetCharacterId, long movedCharacterId, float x, float y) {
        var ctx = contextsByCharacterId.get(targetCharacterId);
        if (ctx == null) {
            return;
        }
        var event = CharacterMovedEvent.newBuilder()
                .setCharacterId(movedCharacterId)
                .setX(x)
                .setY(y)
                .build();
        send(ctx, Opcode.OPCODE_CHARACTER_MOVED_EVENT_VALUE, 0, event.toByteArray());
    }

    @Override
    public void onMonsterSpawned(long targetCharacterId, WorldOutboundSink.MonsterSnapshot monster) {
        var ctx = contextsByCharacterId.get(targetCharacterId);
        if (ctx == null) {
            return;
        }
        var event = MonsterSpawnedEvent.newBuilder()
                .setMonsterId(monster.monsterId())
                .setMonsterCode(monster.monsterCode())
                .setX(monster.x())
                .setY(monster.y())
                .setCurrentHp(monster.currentHp())
                .setMaxHp(monster.maxHp())
                .build();
        send(ctx, Opcode.OPCODE_MONSTER_SPAWNED_EVENT_VALUE, 0, event.toByteArray());
    }

    @Override
    public void onMonsterDespawned(long targetCharacterId, long monsterId) {
        var ctx = contextsByCharacterId.get(targetCharacterId);
        if (ctx == null) {
            return;
        }
        var event = MonsterDespawnedEvent.newBuilder().setMonsterId(monsterId).build();
        send(ctx, Opcode.OPCODE_MONSTER_DESPAWNED_EVENT_VALUE, 0, event.toByteArray());
    }

    @Override
    public void onMonsterMoved(long targetCharacterId, long monsterId, float x, float y) {
        var ctx = contextsByCharacterId.get(targetCharacterId);
        if (ctx == null) {
            return;
        }
        var event = MonsterMovedEvent.newBuilder().setMonsterId(monsterId).setX(x).setY(y).build();
        send(ctx, Opcode.OPCODE_MONSTER_MOVED_EVENT_VALUE, 0, event.toByteArray());
    }

    @Override
    public void onAttackResult(long targetCharacterId, WorldOutboundSink.AttackOutcome outcome) {
        var ctx = contextsByCharacterId.get(targetCharacterId);
        if (ctx == null) {
            return;
        }
        var event = AttackResultEvent.newBuilder()
                .setAttackerEntityId(outcome.attackerEntityId())
                .setTargetEntityId(outcome.targetEntityId())
                .setDamage(outcome.damage())
                .setIsCrit(outcome.crit())
                .setIsMiss(outcome.miss())
                .setTargetCurrentHp(outcome.targetCurrentHp())
                .setTargetDied(outcome.targetDied())
                .build();
        send(ctx, Opcode.OPCODE_ATTACK_RESULT_EVENT_VALUE, 0, event.toByteArray());
    }

    @Override
    public void onSkillResult(long targetCharacterId, WorldOutboundSink.SkillOutcome outcome) {
        var ctx = contextsByCharacterId.get(targetCharacterId);
        if (ctx == null) {
            return;
        }
        var event = SkillResultEvent.newBuilder()
                .setSkillCode(outcome.skillCode())
                .setAttackerEntityId(outcome.attackerEntityId())
                .setTargetEntityId(outcome.targetEntityId())
                .setDamage(outcome.damage())
                .setIsCrit(outcome.crit())
                .setIsMiss(outcome.miss())
                .setTargetCurrentHp(outcome.targetCurrentHp())
                .setTargetDied(outcome.targetDied())
                .build();
        send(ctx, Opcode.OPCODE_SKILL_RESULT_EVENT_VALUE, 0, event.toByteArray());
    }

    @Override
    public void onMonsterKilled(long killerCharacterId, String monsterCode) {
        lootExecutor.execute(() -> grantLootBlocking(killerCharacterId, monsterCode));
    }

    private void grantLootBlocking(long killerCharacterId, String monsterCode) {
        var granted = inventoryFacade.grantLoot(killerCharacterId, monsterCode);
        var ctx = contextsByCharacterId.get(killerCharacterId);
        if (ctx == null) {
            return; // đã ngắt kết nối giữa lúc roll loot — item đã lưu DB thật, chỉ mất thông báo.
        }
        for (var stack : granted) {
            var event = ItemGrantedEvent.newBuilder()
                    .setItemTemplateId(stack.itemTemplateId())
                    .setQuantity(stack.quantity())
                    .build();
            send(ctx, Opcode.OPCODE_ITEM_GRANTED_EVENT_VALUE, 0, event.toByteArray());
        }
    }

    /** Dừng {@link #lootExecutor} (SERVER.md §10 graceful shutdown) — gọi sau khi world tick dừng. */
    void stop() {
        lootExecutor.shutdown();
    }

    private static void send(ChannelHandlerContext ctx, int opcode, int sequenceId, byte[] payload) {
        var header = new PacketHeader(opcode, ProtocolVersion.CURRENT, sequenceId);
        ctx.writeAndFlush(new PacketCodec.Frame(header, payload));
    }
}

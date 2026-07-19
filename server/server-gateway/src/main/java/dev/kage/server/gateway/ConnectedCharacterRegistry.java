package dev.kage.server.gateway;

import dev.kage.protocol.CharacterDespawnedEvent;
import dev.kage.protocol.CharacterMovedEvent;
import dev.kage.protocol.CharacterSpawnedEvent;
import dev.kage.protocol.MoveResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;
import dev.kage.server.world.WorldOutboundSink;
import io.netty.channel.ChannelHandlerContext;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    private final Map<Long, ChannelHandlerContext> contextsByCharacterId = new ConcurrentHashMap<>();

    void register(long characterId, ChannelHandlerContext ctx) {
        contextsByCharacterId.put(characterId, ctx);
    }

    void unregister(long characterId) {
        contextsByCharacterId.remove(characterId);
    }

    @Override
    public void onMoveAccepted(long characterId, int sequenceId, float x, float y) {
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

    private static void send(ChannelHandlerContext ctx, int opcode, int sequenceId, byte[] payload) {
        var header = new PacketHeader(opcode, ProtocolVersion.CURRENT, sequenceId);
        ctx.writeAndFlush(new PacketCodec.Frame(header, payload));
    }
}

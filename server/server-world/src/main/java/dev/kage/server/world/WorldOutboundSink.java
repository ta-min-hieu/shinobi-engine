package dev.kage.server.world;

/**
 * Port đầu ra của server-world — thực thi bởi {@code server-gateway} (Port/Adapter, giống
 * {@code CharacterPort}/{@code AccountPort} ở feature-*). server-world KHÔNG biết Netty
 * (server/CLAUDE.md), nên mọi kết quả tick (SERVER.md §2: "Response/Event vào outbound queue")
 * đi qua interface này thay vì gọi thẳng {@code ChannelHandlerContext}.
 *
 * <p>Mọi phương thức được gọi TRÊN world tick thread (SERVER.md §3) — implementation phía
 * gateway chỉ được tra cứu channel theo {@code characterId} rồi {@code ctx.writeAndFlush(...)}
 * (an toàn gọi từ thread khác EventLoop, Netty tự chuyển task), KHÔNG được block.
 */
public interface WorldOutboundSink {

    /**
     * Server đã xử lý xong 1 {@code MoveCommand} — trả {@code MoveResponse} về ĐÚNG connection đã
     * gửi lệnh, khớp {@code sequenceId} để client reconciliation (CLIENT.md §6).
     */
    void onMoveAccepted(long characterId, int sequenceId, float x, float y);

    /** Nhân vật {@code spawnedCharacterId} VỪA vào AoI của {@code targetCharacterId}. */
    void onCharacterSpawned(long targetCharacterId, long spawnedCharacterId, float x, float y);

    /** Nhân vật {@code despawnedCharacterId} VỪA rời AoI của {@code targetCharacterId}. */
    void onCharacterDespawned(long targetCharacterId, long despawnedCharacterId);

    /** Nhân vật {@code movedCharacterId} (đã ở trong AoI từ trước) vừa đổi vị trí. */
    void onCharacterMoved(long targetCharacterId, long movedCharacterId, float x, float y);
}

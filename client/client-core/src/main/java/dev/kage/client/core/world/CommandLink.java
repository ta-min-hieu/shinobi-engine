package dev.kage.client.core.world;

import java.io.IOException;

import dev.kage.client.core.net.GameConnection;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Phần của {@link GameConnection} mà {@link WorldCommandSender} thật sự dùng — sáu thao tác, không
 * hơn.
 *
 * <p>Tồn tại vì một lý do duy nhất: cho phép dựng {@link WorldScreen} THẬT trong test với một đường
 * truyền giả (đẩy gói vào hàng đợi đến, đếm lệnh gửi đi) — xem {@code InboundPacketFuzzTest}. Không
 * đổi hành vi: bản chạy thật luôn đi qua {@link #of(GameConnection)}, uỷ nhiệm thẳng từng lời gọi.
 */
interface CommandLink {

    /** Xem {@link GameConnection#send(int, int, byte[])}. */
    void send(int opcode, int sequenceId, byte[] payload) throws IOException;

    /** Xem {@link GameConnection#pollInbound()} — {@code null} khi hàng đợi rỗng. */
    PacketCodec.Frame pollInbound();

    /** Xem {@link GameConnection#isConnected()}. */
    boolean isConnected();

    /** Xem {@link GameConnection#connectionGeneration()}. */
    int connectionGeneration();

    /** Xem {@link GameConnection#nextSequenceId()}. */
    int nextSequenceId();

    /** Xem {@link GameConnection#stop()}. */
    void stop();

    /** Đường truyền thật — uỷ nhiệm thẳng, không thêm gì. */
    static CommandLink of(GameConnection connection) {
        return new CommandLink() {
            @Override
            public void send(int opcode, int sequenceId, byte[] payload) throws IOException {
                connection.send(opcode, sequenceId, payload);
            }

            @Override
            public PacketCodec.Frame pollInbound() {
                return connection.pollInbound();
            }

            @Override
            public boolean isConnected() {
                return connection.isConnected();
            }

            @Override
            public int connectionGeneration() {
                return connection.connectionGeneration();
            }

            @Override
            public int nextSequenceId() {
                return connection.nextSequenceId();
            }

            @Override
            public void stop() {
                connection.stop();
            }
        };
    }
}

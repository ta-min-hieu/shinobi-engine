package dev.kage.protocol.wire;

import java.nio.ByteBuffer;

/**
 * Encode/decode HEADER packet (NETWORK.md §102) — thuần {@code java.nio.ByteBuffer}, KHÔNG phụ
 * thuộc Netty. Dùng chung giữa {@code server-gateway} (Netty) và {@code client-core} (Socket
 * thuần) để đảm bảo hai bên nhất quán tuyệt đối mà không phá ArchUnit (chỉ server-gateway được
 * import Netty — Điều 23).
 *
 * <p>Framing (4-byte length prefix bọc ngoài frame này) là việc của tầng transport
 * (Netty {@code LengthFieldPrepender}/{@code LengthFieldBasedFrameDecoder} phía server;
 * {@link java.io.DataOutputStream}/{@link java.io.DataInputStream} phía client) — codec này chỉ
 * xử lý header + payload BÊN TRONG một frame đã được tách xong.
 */
public final class PacketCodec {

    /** opcode(2) + protocolVersion(1) + sequenceId(4). */
    private static final int HEADER_SIZE = 7;

    private PacketCodec() {
    }

    public static byte[] encodeFrame(PacketHeader header, byte[] payload) {
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + payload.length);
        buffer.putShort((short) header.opcode());
        buffer.put((byte) header.protocolVersion());
        buffer.putInt(header.sequenceId());
        buffer.put(payload);
        return buffer.array();
    }

    public static Frame decodeFrame(byte[] frame) {
        if (frame.length < HEADER_SIZE) {
            throw new IllegalArgumentException(
                    "Frame quá ngắn để chứa header (" + HEADER_SIZE + " byte): " + frame.length);
        }
        ByteBuffer buffer = ByteBuffer.wrap(frame);
        int opcode = Short.toUnsignedInt(buffer.getShort());
        int protocolVersion = Byte.toUnsignedInt(buffer.get());
        int sequenceId = buffer.getInt();
        byte[] payload = new byte[frame.length - HEADER_SIZE];
        buffer.get(payload);
        return new Frame(new PacketHeader(opcode, protocolVersion, sequenceId), payload);
    }

    /**
     * Cặp (header, payload) — dùng cho CẢ HAI chiều: kết quả của {@link #decodeFrame} (nhận),
     * và đầu vào để tạo frame mới trước khi gọi {@link #encodeFrame} (gửi).
     *
     * <p>⚠️ {@code payload} là {@code byte[]} — record tự sinh {@code equals()} so sánh THAM
     * CHIẾU mảng, không so nội dung (cạm bẫy Java record kinh điển). Test round-trip phải so
     * sánh {@code header} và nội dung {@code payload} (VD {@code Arrays.equals}) RIÊNG, không
     * dùng {@code equals()} mặc định của record này.
     */
    public record Frame(PacketHeader header, byte[] payload) {
    }
}

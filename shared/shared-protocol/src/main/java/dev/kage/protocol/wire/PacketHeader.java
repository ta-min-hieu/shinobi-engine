package dev.kage.protocol.wire;

/**
 * Header packet (NETWORK.md §102) — KHÔNG phải Protobuf, chỉ payload mới là Protobuf.
 *
 * <p>{@code opcode}: uint16 (0–65535) — dải theo domain, xem {@code opcode_registry.proto}.
 * {@code protocolVersion}: uint8 (0–255) — xem {@link ProtocolVersion}.
 * {@code sequenceId}: dùng int trong Java cho đơn giản (đủ cho vòng đời 1 kết nối MVP); wire
 * format vẫn là uint32 4-byte, xem {@link PacketCodec}.
 */
public record PacketHeader(int opcode, int protocolVersion, int sequenceId) {

    public PacketHeader {
        if (opcode < 0 || opcode > 0xFFFF) {
            throw new IllegalArgumentException("opcode ngoài phạm vi uint16: " + opcode);
        }
        if (protocolVersion < 0 || protocolVersion > 0xFF) {
            throw new IllegalArgumentException("protocolVersion ngoài phạm vi uint8: " + protocolVersion);
        }
    }
}

package dev.kage.protocol.wire;

/**
 * Version protocol hiện tại (NETWORK.md §106). Tăng khi có breaking change ở tầng header hoặc
 * cách encode chung — KHÔNG tăng khi chỉ thêm field protobuf mới (đó là tương thích ngược).
 */
public final class ProtocolVersion {

    public static final int CURRENT = 1;

    private ProtocolVersion() {}
}

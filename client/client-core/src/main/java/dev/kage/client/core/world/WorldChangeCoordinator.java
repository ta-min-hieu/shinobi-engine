package dev.kage.client.core.world;

import java.util.function.Consumer;

import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.protocol.Opcode;
import dev.kage.protocol.WorldChangedEvent;
import dev.kage.protocol.WorldKind;
import dev.kage.protocol.wire.PacketCodec;

/**
 * ADR-051 — lối vào DUY NHẤT của mọi cú đổi World phía client: hầm ngục vào/ra/gục, đấu trường
 * vào/ra. Trước ADR-051 giao thức không có gói "đổi World" nào, nên hầm ngục tự dọn màn hình theo
 * Response của chính lệnh vào/ra, còn đấu trường thì không dọn gì cả.
 *
 * <p>Server ghi {@code WorldChangedEvent} vào kênh TRƯỚC khi attach vào World mới (ADR-051 D2), nên
 * dọn ở đây không bao giờ xoá mất thực thể của World mới — khác cú dọn theo Response cũ, vốn có thể
 * tới SAU spawn đầu tiên (ADR-047 "Phát hiện phụ").
 *
 * <p>Thuần luồng gói, không vẽ gì: việc dọn cụ thể (presenter, vị trí, máu) do {@code WorldScreen}
 * cài qua {@link #setOnWorldChanged}; dòng báo và cờ "đang ở hầm ngục" do {@link DungeonCoordinator}.
 */
final class WorldChangeCoordinator implements WindowFlow {

    private Consumer<WorldChangedEvent> onWorldChanged = event -> {};
    private WorldKind currentKind = WorldKind.WORLD_KIND_MAIN;

    /** Việc phải làm khi server báo đổi World. Mặc định không làm gì — lớp này tự chạy được trong test. */
    void setOnWorldChanged(Consumer<WorldChangedEvent> listener) {
        this.onWorldChanged = listener;
    }

    /** World mà server nói nhân vật đang ở — {@code MAIN} cho tới gói đổi World đầu tiên. */
    WorldKind currentKind() {
        return currentKind;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_WORLD_CHANGED_EVENT_VALUE) {
            return false;
        }
        WorldChangedEvent event = WorldChangedEvent.parseFrom(frame.payload());
        currentKind = event.getWorldKind();
        onWorldChanged.accept(event);
        return true;
    }

    /** Rời màn hình/mất kết nối — vào lại world luôn bắt đầu ở world chính. */
    @Override
    public void reset() {
        currentKind = WorldKind.WORLD_KIND_MAIN;
    }
}

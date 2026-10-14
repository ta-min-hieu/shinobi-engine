package dev.kage.client.core.net;

import java.util.ArrayList;
import java.util.List;

import dev.kage.protocol.wire.PacketCodec;

/**
 * Gói tới trong lúc chờ {@code SelectCharacterResponse}, giữ lại để màn world xử lý sau
 * ({@link GameConnection#replayBeforeQueued}) thay vì vứt.
 *
 * <p><b>Vì sao</b> (chủ dự án chốt 2026-10-14, docs/README.md dòng 73(c)): server nay gắn nhân vật vào
 * world TRƯỚC khi trả lời, để lệnh đầu tiên gửi ngay sau câu trả lời không bị {@code error.not_authenticated}.
 * Đổi lại, vòng tick có thể phát gói spawn tới TRƯỚC câu trả lời — và spawn thì server không phát lại.
 *
 * <p>Có trần {@value #MAX_HELD}: quá trần thì gói MỚI bị bỏ (kèm một dòng log) — hàng chờ chỉ dài vài
 * gói trong thực tế; trần chỉ để một phiên chờ bất thường không phình bộ nhớ. Chỉ dùng ở render thread.
 */
public final class HeldFrames {

    /** Trần số gói giữ lại trong MỘT lần chờ. */
    static final int MAX_HELD = 512;

    private final List<PacketCodec.Frame> frames = new ArrayList<>();
    private int dropped;

    /** Giữ thêm một gói (theo đúng thứ tự tới). */
    public void hold(PacketCodec.Frame frame) {
        if (frames.size() < MAX_HELD) {
            frames.add(frame);
        } else {
            dropped++;
        }
    }

    /**
     * Trả mọi gói đang giữ về đầu hàng đợi của {@code connection} rồi xoá sạch.
     *
     * @return số gói bị bỏ vì vượt trần (để bên gọi log)
     */
    public int replayInto(GameConnection connection) {
        connection.replayBeforeQueued(frames);
        return clear();
    }

    /** Trả gói đang giữ dưới dạng bản sao, rồi xoá sạch — cho bên không cầm {@link GameConnection}. */
    public List<PacketCodec.Frame> drain() {
        List<PacketCodec.Frame> copy = List.copyOf(frames);
        clear();
        return copy;
    }

    /**
     * Vứt mọi gói đang giữ — lần chờ thất bại (bị từ chối, hết giờ, chọn lại nhân vật khác).
     *
     * @return số gói đã bị bỏ vì vượt trần trong lần chờ vừa xong
     */
    public int clear() {
        int droppedCount = dropped;
        frames.clear();
        dropped = 0;
        return droppedCount;
    }

    /** Số gói đang giữ — cho test. */
    int size() {
        return frames.size();
    }
}

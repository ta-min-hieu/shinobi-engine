package dev.kage.server.world;

/**
 * Nhận vị trí THẬT của một nhân vật ngay lúc World gỡ nó ra (dòng 76(a), chủ dự án duyệt 2026-10-17).
 *
 * <p>Trước đó gateway lưu vị trí lúc ngắt kết nối từ mẫu {@link WorldOutboundSink#onCharacterPositionSampled}
 * gần nhất — 1 lần mỗi giây, nên rớt mạng/tắt server giữa lúc chạy còn lùi tới ~5 m. Lệnh gỡ mang theo
 * listener này; World gọi nó TRÊN TICK THREAD, đúng tick xử lý lệnh gỡ, trước khi xoá entity. Nhân vật
 * không còn trong World thì không gọi gì.
 *
 * <p>Cài đặt phải rẻ và không chặn (chỉ ghi lại hai số) — luật tick thread của SERVER.md §2.
 */
@FunctionalInterface
public interface FinalPositionListener {

    /** Không ai cần vị trí cuối. */
    FinalPositionListener NONE = (x, y) -> {};

    void onFinalPosition(float x, float y);
}

package dev.kage.server.world;

/**
 * Tham số một đoàn hộ tống (ADR-114 D1) — đọc từ {@code escort_route} ở gateway rồi truyền vào; server-world không biết
 * quest là gì, chỉ biết "đoàn mang mã này đi từ A tới B".
 *
 * @param code mã tuyến (= mã quest), báo lại nguyên vẹn trong {@link WorldOutboundSink.EscortSnapshot}
 * @param startX điểm xuất phát
 * @param endX điểm đến; đoàn đi thẳng theo trục X
 * @param maxHp máu tối đa của đoàn
 * @param speedMps tốc độ (m/s) khi người hộ tống đứng đủ gần
 */
public record EscortRoute(String code, float startX, float endX, int maxHp, float speedMps) {

    public EscortRoute {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code rỗng");
        }
        if (maxHp <= 0 || speedMps <= 0f) {
            throw new IllegalArgumentException("maxHp/speedMps phải > 0: " + maxHp + "/" + speedMps);
        }
    }
}

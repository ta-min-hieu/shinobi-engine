package dev.kage.engine.ecs.system;

/**
 * Phán quyết độc lập "đoạn thẳng có chạm hình chữ nhật không", tính bằng {@code double} và có BIÊN:
 * ca sát mép (chạm vừa đủ, sượt góc) là ca mà sai số làm tròn {@code float} được phép ngả về bên nào
 * cũng được, nên bộ kiểm chỉ đối chiếu ở hai phán quyết chắc chắn.
 */
final class SegmentOracle {

    /** Kết luận của phép đối chiếu. */
    enum Verdict {
        /** Đoạn đi SÂU qua hộp ít nhất {@link #MARGIN} — bản thật bắt buộc phải báo chạm. */
        CLEARLY_CROSSES,
        /** Đoạn cách hộp ít nhất {@link #MARGIN} — bản thật bắt buộc phải báo không chạm. */
        CLEARLY_MISSES,
        /** Sát mép — không đối chiếu. */
        TOO_CLOSE_TO_CALL
    }

    /** Biên theo mét. Sai số float ở toạ độ cỡ ±100m là ~1e-5m; 1mm là dư hai bậc. */
    static final double MARGIN = 1e-3;

    private SegmentOracle() {}

    /** Đoạn thẳng {@code (x0, y0) → (x1, y1)}. */
    record Segment(double x0, double y0, double x1, double y1) {}

    /** Khoảng tham số {@code [from, to]} còn lại của đoạn sau khi cắt theo từng trục. */
    private static final class Span {
        private double from;
        private double to = 1.0;

        /** Thu hẹp theo một trục; {@code false} khi khoảng thành rỗng. */
        boolean clip(double start, double delta, double min, double max) {
            if (delta == 0.0) {
                return start >= min && start <= max;
            }
            double near = (min - start) / delta;
            double far = (max - start) / delta;
            from = Math.max(from, Math.min(near, far));
            to = Math.min(to, Math.max(near, far));
            return from <= to;
        }
    }

    static Verdict judge(Aabb box, Segment segment) {
        if (touches(box, -MARGIN, segment)) {
            return Verdict.CLEARLY_CROSSES;
        }
        if (!touches(box, MARGIN, segment)) {
            return Verdict.CLEARLY_MISSES;
        }
        return Verdict.TOO_CLOSE_TO_CALL;
    }

    /** Đoạn có chạm hộp đã NỚI mỗi phía {@code grow} mét không ({@code grow} âm = co lại). */
    private static boolean touches(Aabb box, double grow, Segment segment) {
        double minX = box.minX() - grow;
        double maxX = box.maxX() + grow;
        double minY = box.minY() - grow;
        double maxY = box.maxY() + grow;
        if (minX > maxX || minY > maxY) {
            return false; // hộp co lại thành rỗng
        }
        Span span = new Span();
        return span.clip(segment.x0(), segment.x1() - segment.x0(), minX, maxX)
                && span.clip(segment.y0(), segment.y1() - segment.y0(), minY, maxY);
    }
}

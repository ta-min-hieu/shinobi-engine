package dev.kage.tools.loadtest.probe;

import java.util.OptionalLong;

/**
 * Chọn MỤC TIÊU đánh của vòng quan sát — tách khỏi {@link GameProbe} để kiểm được bằng test thuần.
 *
 * <p><b>Vì sao không còn "chốt một lần trước vòng lặp"</b> (kiểm sống chuỗi quest arc 1, 2026-10-03):
 * cách cũ dính hai bẫy đã ghi trong sổ tay dự án, cả hai đều bịa ra trận THUA không có thật —
 *
 * <ol>
 *   <li><b>Chưa thấy quái lúc bắt đầu</b> (bẫy "nearest-target race"): đứng sẵn gần đích thì vòng
 *       quan sát mở TRƯỚC khi {@code MonsterSpawnedEvent} đầu tiên tới, probe không có mục tiêu và
 *       đứng im chịu đòn suốt cửa sổ. ⇒ Với {@code nearest}, còn rỗng thì chọn lại ở MỖI vòng tới khi
 *       thấy con đầu tiên. Đây là sửa lỗi công cụ, bật mặc định.
 *   <li><b>Mục tiêu chết là ngừng đánh vĩnh viễn</b> (bẫy "target locked once"): con hồi sinh đánh
 *       một người chơi không đánh trả. ⇒ {@code --retargetOnKill=true} chọn con gần nhất kế tiếp khi
 *       mục tiêu chết/rời tầm nhìn. Để sau cờ vì nó ĐỔI ý nghĩa một lần chạy (từ "một trận" thành
 *       "đánh tới hết cửa sổ") — các phép đo cũ dựa trên nghĩa "một trận" vẫn tái lập được.
 * </ol>
 *
 * <p>{@code --attackCode=<monster_code>} giới hạn {@code nearest} vào đúng MỘT loài — trên trục chính
 * con gần nhất lúc đứng lại hay là một con khác loài đang đuổi theo từ chặng trước.
 */
final class TargetSelector {

    private final boolean nearest;
    private final boolean retargetOnKill;
    private final String attackCode;
    private OptionalLong current;

    TargetSelector(ProbeConfig config, WorldObservation observation) {
        this.nearest = config.attacksNearest();
        this.retargetOnKill = config.retargetOnKill();
        this.attackCode = config.attackCode();
        this.current = nearest ? observation.nearestMonsterId(attackCode) : config.explicitAttackTargetId();
    }

    /** Mục tiêu hiện tại, rỗng nếu chưa có. */
    OptionalLong current() {
        return current;
    }

    /**
     * Xem lại mục tiêu theo ảnh chụp thế giới mới nhất.
     *
     * @return mục tiêu MỚI nếu vừa đổi (người gọi phải gửi {@code SetTargetCommand}), rỗng nếu giữ nguyên
     */
    OptionalLong refresh(WorldObservation observation) {
        if (!nearest) {
            return OptionalLong.empty();
        }
        boolean needsNew =
                current.isEmpty() || (retargetOnKill && !observation.isInAreaOfInterest(current.getAsLong()));
        if (!needsNew) {
            return OptionalLong.empty();
        }
        OptionalLong next = observation.nearestMonsterId(attackCode);
        if (next.isEmpty() || next.equals(current)) {
            return OptionalLong.empty();
        }
        current = next;
        return next;
    }
}

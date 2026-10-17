package dev.kage.engine.ecs;

/**
 * Bọc {@code com.badlogic.ashley.core.Family} — bộ lọc Entity theo Component (ECS.md §5).
 *
 * <p>{@code Family.all(Position.class, Velocity.class).get()}
 *
 * <p><b>An toàn khi dựng từ nhiều luồng (sửa 2026-10-10).</b> {@code Family.all} của Ashley ghi tập
 * component vào MỘT builder tĩnh dùng chung, rồi {@code get()} đọc lại builder đó và tra/ghi một bảng
 * tĩnh — không bước nào đồng bộ. Server dựng mỗi World trên luồng của lệnh sinh ra nó (trận đấu
 * trường, hầm ngục, khu vực mới), nên hai World dựng cùng lúc thì System của World này nhận Family
 * của System kia: lọc nhầm tập entity, ném {@code NullPointerException} mỗi tick hoặc lặng lẽ không
 * bao giờ chạy. Nay {@link Builder} tự giữ tập component của nó và chỉ chạm trạng thái tĩnh của
 * Ashley trong {@link Builder#get()}, dưới một khoá chung — đường nguội (chỉ chạy lúc dựng System),
 * không nằm trong vòng tick. Xem {@code FamilyConcurrencyTest}.
 */
public final class Family {

    private final com.badlogic.ashley.core.Family ashleyFamily;

    private Family(com.badlogic.ashley.core.Family ashleyFamily) {
        this.ashleyFamily = ashleyFamily;
    }

    // "varargs": chỉ sao chép mảng varargs rồi đọc — không ghi gì vào nó, nên không có heap pollution.
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static Builder all(Class<? extends Component>... types) {
        return new Builder(types.clone());
    }

    /** Chỉ package này (KSystem) cần Family gốc để tạo Ashley IteratingSystem. */
    com.badlogic.ashley.core.Family raw() {
        return ashleyFamily;
    }

    public static final class Builder {
        private final Class<? extends Component>[] types;

        private Builder(Class<? extends Component>[] types) {
            this.types = types;
        }

        public Family get() {
            return new Family(AshleyStatics.family(types));
        }
    }
}

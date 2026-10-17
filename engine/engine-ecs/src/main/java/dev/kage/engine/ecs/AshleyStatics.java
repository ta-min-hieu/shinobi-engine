package dev.kage.engine.ecs;

/**
 * MỌI lần chạm trạng thái tĩnh của Ashley từ module này đi qua đây — bảng {@code Family} tĩnh và sổ
 * {@code ComponentType} tĩnh, cả hai đều không đồng bộ (sửa 2026-10-10 cho Family, 2026-10-17 cho
 * ComponentType — dòng 73(d) bảng chờ). Khoá là field riêng của lớp này, không lộ ra ngoài. Chỉ chạy ở
 * đường nguội (dựng System/World, đăng ký kiểu), không nằm trong vòng tick.
 */
final class AshleyStatics {

    private static final Object LOCK = new Object();

    private AshleyStatics() {}

    static com.badlogic.ashley.core.Family family(Class<? extends Component>[] types) {
        synchronized (LOCK) {
            return com.badlogic.ashley.core.Family.all(types).get();
        }
    }

    static <T extends Component> com.badlogic.ashley.core.ComponentMapper<T> mapper(Class<T> type) {
        synchronized (LOCK) {
            return com.badlogic.ashley.core.ComponentMapper.getFor(type);
        }
    }

    static void registerTypes(Class<? extends Component>[] types) {
        synchronized (LOCK) {
            for (Class<? extends Component> type : types) {
                com.badlogic.ashley.core.ComponentType.getFor(type);
            }
        }
    }
}

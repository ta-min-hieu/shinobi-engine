package dev.kage.engine.ecs;

/**
 * Bốn loại component thử và các Family dựng từ chúng, cho bộ kiểm dựa trên mô hình
 * ({@link EcsFuzzRun}). Loại được gọi bằng chỉ số {@code 0..COUNT-1}; tổ hợp bằng mặt nạ bit.
 */
final class ProbeKinds {

    static final int COUNT = 4;

    private static final ComponentMapper<Alpha> ALPHA = ComponentMapper.getFor(Alpha.class);
    private static final ComponentMapper<Beta> BETA = ComponentMapper.getFor(Beta.class);
    private static final ComponentMapper<Gamma> GAMMA = ComponentMapper.getFor(Gamma.class);
    private static final ComponentMapper<Delta> DELTA = ComponentMapper.getFor(Delta.class);

    private ProbeKinds() {}

    /** {@code owner} = id của entity đang mang component — lệch là dấu hiệu trỏ nhầm entity. */
    abstract static class ProbeComponent implements Component {
        long owner;
    }

    static final class Alpha extends ProbeComponent {}

    static final class Beta extends ProbeComponent {}

    static final class Gamma extends ProbeComponent {}

    static final class Delta extends ProbeComponent {}

    static ProbeComponent create(int kind) {
        return switch (kind) {
            case 0 -> new Alpha();
            case 1 -> new Beta();
            case 2 -> new Gamma();
            case 3 -> new Delta();
            default -> throw new IllegalArgumentException("kind " + kind);
        };
    }

    static boolean mapperHas(int kind, KEntity entity) {
        return mapper(kind).has(entity);
    }

    static boolean entityHas(int kind, KEntity entity) {
        return switch (kind) {
            case 0 -> entity.has(Alpha.class);
            case 1 -> entity.has(Beta.class);
            case 2 -> entity.has(Gamma.class);
            case 3 -> entity.has(Delta.class);
            default -> throw new IllegalArgumentException("kind " + kind);
        };
    }

    /** {@code true} khi mapper trả ĐÚNG đối tượng {@code expected} (component không định nghĩa equals). */
    static boolean mapperReturns(int kind, KEntity entity, ProbeComponent expected) {
        return expected.equals(mapper(kind).get(entity));
    }

    /** Family của tổ hợp {@code mask}; {@code 0} là Family rỗng — khớp MỌI entity. */
    static Family family(int mask) {
        return switch (mask) {
            case 0 -> Family.all().get();
            case 0b0001 -> Family.all(Alpha.class).get();
            case 0b0010 -> Family.all(Beta.class).get();
            case 0b0011 -> Family.all(Alpha.class, Beta.class).get();
            case 0b0111 -> Family.all(Alpha.class, Beta.class, Gamma.class).get();
            case 0b1000 -> Family.all(Delta.class).get();
            default -> throw new IllegalArgumentException("mask " + mask);
        };
    }

    private static ComponentMapper<? extends ProbeComponent> mapper(int kind) {
        return switch (kind) {
            case 0 -> ALPHA;
            case 1 -> BETA;
            case 2 -> GAMMA;
            case 3 -> DELTA;
            default -> throw new IllegalArgumentException("kind " + kind);
        };
    }
}

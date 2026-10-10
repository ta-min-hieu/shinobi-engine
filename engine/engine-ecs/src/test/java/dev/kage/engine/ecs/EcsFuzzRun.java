package dev.kage.engine.ecs;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;

import dev.kage.engine.ecs.EcsModel.ModelEntity;
import dev.kage.engine.ecs.ProbeKinds.ProbeComponent;

/**
 * Một lượt chạy ngẫu nhiên CÓ SEED: cùng một chuỗi thao tác áp lên {@link EcsWorld} thật và lên
 * {@link EcsModel}, đối chiếu sau MỖI bước. Thao tác ngoài tick: tạo/gỡ entity, thêm/ghi đè
 * component, gỡ và ghi qua handle đã chết, {@code clear()}. Trong tick, System tự gỡ chính entity
 * đang xử lý, gỡ entity khác, tạo entity mới, thêm component, hoặc ném lỗi giữa chừng.
 *
 * <p>Ngữ nghĩa lặp mà mô hình khẳng định, cho từng System trong từng tick: xử lý ĐÚNG MỘT LẦN mỗi
 * entity thuộc Family lúc System bắt đầu, cộng mọi entity gia nhập Family trong chính lượt đó; không
 * xử lý entity nào khác. Entity bị gỡ giữa lượt còn sống tới hết lượt của System đang chạy.
 */
final class EcsFuzzRun {

    /**
     * Thao tác, kèm trọng số chọn ngẫu nhiên: ngoài tick lúc dân số đang tăng / đang giảm, rồi giữa
     * lượt lặp lúc đang tăng / đang giảm. Trọng số 0 = không áp được ở ngữ cảnh đó.
     */
    enum Op {
        CREATE(120, 32, 25, 8),
        REMOVE(32, 120, 12, 25),
        REMOVE_SELF(0, 0, 12, 25),
        ADD_COMPONENT(64, 64, 25, 25),
        REMOVE_STALE(24, 24, 10, 10),
        ADD_TO_STALE(24, 24, 10, 10),
        CLEAR(1, 1, 0, 0),
        TICK_PURE(48, 48, 0, 0),
        TICK_MUTATING(72, 72, 0, 0),
        TICK_THROWING(12, 12, 0, 0);

        private final int growing;
        private final int shrinking;
        private final int midPassGrowing;
        private final int midPassShrinking;

        Op(int growing, int shrinking, int midPassGrowing, int midPassShrinking) {
            this.growing = growing;
            this.shrinking = shrinking;
            this.midPassGrowing = midPassGrowing;
            this.midPassShrinking = midPassShrinking;
        }

        int weight(boolean midPass, boolean populationGrowing) {
            if (midPass) {
                return populationGrowing ? midPassGrowing : midPassShrinking;
            }
            return populationGrowing ? growing : shrinking;
        }

        static Op pick(SplittableRandom random, boolean midPass, boolean populationGrowing) {
            int total = 0;
            for (Op op : values()) {
                total += op.weight(midPass, populationGrowing);
            }
            int roll = random.nextInt(total);
            for (Op op : values()) {
                roll -= op.weight(midPass, populationGrowing);
                if (roll < 0) {
                    return op;
                }
            }
            throw new IllegalStateException("bảng trọng số rỗng");
        }
    }

    /** Lỗi do chính bộ kiểm ném ra giữa lượt lặp — mô phỏng một System hỏng (ADR-026). */
    static final class ProbeFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private final class ProbeSystem extends IteratingKSystem {
        final int mask;

        ProbeSystem(int mask) {
            super(ProbeKinds.family(mask));
            this.mask = mask;
        }

        @Override
        protected void processEntity(KEntity entity, float deltaTime) {
            onProcessed(this, entity);
        }
    }

    /** Hai System cuối trùng Family và trùng lớp với System đầu — đúng ca Ashley từng âm thầm gỡ. */
    private static final int[] SYSTEM_MASKS = {0b0001, 0b0011, 0, 0b0010, 0b0111, 0b1000, 0b0001};

    private static final float FIXED_DT = 1f / 60f;
    private static final double MUTATION_CHANCE = 0.04;
    private static final int MAX_LIVE = 320;
    private static final int TRACE_KEPT = 40;
    private static final int NOT_TICKING = -1;
    private static final int NEVER = -1;

    private final long seed;
    private final SplittableRandom random;
    private final EcsWorld world = new EcsWorld();
    private final EcsModel model = new EcsModel();
    private final List<ProbeSystem> systems = new ArrayList<>();
    private final List<ModelEntity> pendingRemoval = new ArrayList<>();
    private final Set<Long> expected = new HashSet<>();
    private final List<Long> processed = new ArrayList<>();
    private final List<String> violations = new ArrayList<>();
    private final Deque<String> trace = new ArrayDeque<>();
    private final Map<Op, Integer> opCounts = new EnumMap<>(Op.class);

    private int step;
    private int passIndex = NOT_TICKING;
    private double mutationChance;
    private int throwCountdown = NEVER;
    private boolean growing;
    private int nextPhaseStep;
    private long lastId;
    private long digest;
    private int maxLive;
    private int systemFailures;

    EcsFuzzRun(long seed) {
        this.seed = seed;
        this.random = new SplittableRandom(seed);
    }

    List<String> run(int steps) {
        installSystems();
        for (step = 0; step < steps && violations.isEmpty(); step++) {
            if (step >= nextPhaseStep) {
                growing = !growing;
                nextPhaseStep = step + 50 + random.nextInt(350);
            }
            if (model.live().size() > MAX_LIVE) {
                growing = false;
            }
            applyOutsideTick(Op.pick(random, false, growing));
            model.compareWith(world, violations);
            maxLive = Math.max(maxLive, model.live().size());
        }
        return violations;
    }

    /** Dấu vân tay của lượt chạy: thứ tự thao tác và thứ tự System xử lý từng entity. */
    long digest() {
        return digest;
    }

    int maxLive() {
        return maxLive;
    }

    int systemFailures() {
        return systemFailures;
    }

    int count(Op op) {
        return opCounts.getOrDefault(op, 0);
    }

    String failureReport() {
        return "seed=" + seed + " step=" + step + "\nvi phạm: " + violations + "\nvết (mới nhất cuối):\n  "
                + String.join("\n  ", trace)
                + "\nchạy lại: -Dkage.ecsFuzz.seed=" + seed + " -Dkage.ecsFuzz.seeds=1";
    }

    private void installSystems() {
        systems.clear();
        for (int mask : SYSTEM_MASKS) {
            ProbeSystem system = new ProbeSystem(mask);
            systems.add(system);
            world.addSystem(system);
        }
    }

    private void applyOutsideTick(Op op) {
        note(op, "");
        switch (op) {
            case CLEAR -> clear();
            case TICK_PURE -> tick(0.0, false);
            case TICK_MUTATING -> tick(MUTATION_CHANCE, false);
            case TICK_THROWING -> tick(MUTATION_CHANCE, true);
            default -> applyStructural(op);
        }
    }

    private void mutateMidPass(ModelEntity self) {
        Op op = Op.pick(random, true, growing);
        note(op, " giữa lượt System " + passIndex + ", đang xử lý #" + self.id());
        if (op == Op.REMOVE_SELF) {
            remove(self);
        } else {
            applyStructural(op);
        }
        expected.addAll(model.idsMatching(systems.get(passIndex).mask));
    }

    /** Thao tác sửa cấu trúc — áp được cả ngoài tick lẫn giữa lượt lặp. */
    private void applyStructural(Op op) {
        switch (op) {
            case CREATE -> create();
            case REMOVE -> removeRandomLive();
            case ADD_COMPONENT -> addComponentToRandomLive();
            case REMOVE_STALE -> removeThroughStaleHandle();
            case ADD_TO_STALE -> addThroughStaleHandle();
            default -> throw new IllegalStateException("không phải thao tác sửa cấu trúc: " + op);
        }
    }

    private void note(Op op, String detail) {
        opCounts.merge(op, 1, Integer::sum);
        digest = digest * 31 + op.name().hashCode();
        trace.addLast(step + ": " + op + detail + " (sống=" + model.live().size() + ")");
        if (trace.size() > TRACE_KEPT) {
            trace.removeFirst();
        }
    }

    private void violate(String message) {
        violations.add(message);
    }

    // ---- thao tác nguyên thuỷ --------------------------------------------------------------

    private void create() {
        KEntity handle = world.createEntity();
        if (handle.id() <= lastId) {
            violate("id #" + handle.id() + " không lớn hơn id trước đó #" + lastId);
        }
        lastId = handle.id();
        for (int kind = 0; kind < ProbeKinds.COUNT; kind++) {
            if (ProbeKinds.mapperHas(kind, handle) || ProbeKinds.entityHas(kind, handle)) {
                violate("entity mới #" + handle.id() + " sinh ra đã mang component loại " + kind);
            }
        }
        ModelEntity entity = model.add(handle);
        int mask = random.nextInt(1 << ProbeKinds.COUNT);
        for (int kind = 0; kind < ProbeKinds.COUNT; kind++) {
            if ((mask & (1 << kind)) != 0) {
                attach(entity, kind);
            }
        }
    }

    private void attach(ModelEntity entity, int kind) {
        ProbeComponent existing = entity.components[kind];
        boolean reuseSameInstance = existing != null && random.nextInt(10) == 0;
        ProbeComponent component = reuseSameInstance ? existing : ProbeKinds.create(kind);
        component.owner = entity.id();
        entity.handle.add(component);
        entity.components[kind] = component;
    }

    private Optional<ModelEntity> randomLive() {
        List<ModelEntity> live = model.live();
        return live.isEmpty() ? Optional.empty() : Optional.of(live.get(random.nextInt(live.size())));
    }

    private void addComponentToRandomLive() {
        randomLive().ifPresent(entity -> attach(entity, random.nextInt(ProbeKinds.COUNT)));
    }

    private void removeRandomLive() {
        randomLive().ifPresent(this::remove);
    }

    private void remove(ModelEntity entity) {
        world.removeEntity(entity.handle);
        if (random.nextInt(8) == 0) {
            world.removeEntity(entity.handle); // gỡ hai lần liền nhau phải là no-op
        }
        if (passIndex == NOT_TICKING) {
            model.kill(entity);
        } else if (!pendingRemoval.contains(entity)) {
            pendingRemoval.add(entity);
        }
    }

    private void removeThroughStaleHandle() {
        List<KEntity> stale = model.stale();
        if (!stale.isEmpty()) {
            world.removeEntity(stale.get(random.nextInt(stale.size())));
        }
    }

    private void addThroughStaleHandle() {
        List<KEntity> stale = model.stale();
        if (stale.isEmpty()) {
            return;
        }
        KEntity handle = stale.get(random.nextInt(stale.size()));
        ProbeComponent component = ProbeKinds.create(random.nextInt(ProbeKinds.COUNT));
        component.owner = -handle.id();
        handle.add(component);
    }

    private void clear() {
        world.clear();
        model.killAll();
        installSystems();
    }

    // ---- tick ------------------------------------------------------------------------------

    private void tick(double chance, boolean throwing) {
        mutationChance = chance;
        throwCountdown = throwing ? 1 + random.nextInt(1 + 2 * model.live().size()) : NEVER;
        boolean failed = false;
        beginPass(0);
        try {
            world.update(FIXED_DT, (systemName, durationNanos) -> finishPass());
        } catch (ProbeFailure expectedFailure) {
            failed = true;
            systemFailures++;
            flushModelRemovals();
        }
        if (!failed && passIndex != systems.size()) {
            violate("tick chỉ chạy " + passIndex + "/" + systems.size() + " System");
        }
        passIndex = NOT_TICKING;
        throwCountdown = NEVER;
    }

    private void beginPass(int index) {
        passIndex = index;
        processed.clear();
        expected.clear();
        if (index < systems.size()) {
            expected.addAll(model.idsMatching(systems.get(index).mask));
        }
    }

    private void finishPass() {
        Set<Long> seen = new HashSet<>(processed);
        if (seen.size() != processed.size()) {
            violate("System " + passIndex + " xử lý trùng entity: " + processed);
        }
        if (!seen.equals(expected)) {
            Set<Long> missed = new HashSet<>(expected);
            missed.removeAll(seen);
            Set<Long> extra = new HashSet<>(seen);
            extra.removeAll(expected);
            violate("System " + passIndex + " bỏ sót " + missed + ", xử lý thừa " + extra);
        }
        flushModelRemovals();
        beginPass(passIndex + 1);
    }

    private void flushModelRemovals() {
        for (ModelEntity entity : pendingRemoval) {
            model.kill(entity);
        }
        pendingRemoval.clear();
    }

    private void onProcessed(ProbeSystem system, KEntity entity) {
        if (passIndex < 0 || passIndex >= systems.size() || !system.equals(systems.get(passIndex))) {
            violate("System chạy sai lượt (passIndex=" + passIndex + ")");
            return;
        }
        processed.add(entity.id());
        digest = digest * 31 + entity.id();
        Optional<ModelEntity> known = model.find(entity.id());
        if (known.isEmpty() || !known.get().handle.equals(entity)) {
            violate("System " + passIndex + " nhận entity đã chết hoặc lạ #" + entity.id());
            return;
        }
        throwCountdown--;
        if (throwCountdown == 0) {
            throw new ProbeFailure();
        }
        if (random.nextDouble() < mutationChance) {
            mutateMidPass(known.get());
        }
    }
}

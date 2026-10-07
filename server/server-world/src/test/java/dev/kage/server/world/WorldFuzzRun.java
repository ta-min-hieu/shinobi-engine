package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.server.world.component.HealthComponent;

/**
 * Một lượt chạy của bộ kiểm thuộc tính: {@value #CHARACTER_COUNT} nhân vật bấm lệnh NGẪU NHIÊN qua
 * đúng API công khai mà {@code server-gateway} gọi ({@link WorldFacade}), World được tick TAY từng
 * tick ({@link WorldFacade#runOneTickOnCallerThread()}), mọi bất biến kiểm sau MỖI tick.
 *
 * <p>Lệnh do {@link SplittableRandom} seed cố định sinh ra, và World nhận một nhánh tách từ CÙNG seed
 * làm nguồn random gameplay (trúng/né/bạo kích, hướng tuần tra — production dùng
 * {@code ThreadLocalRandom} qua {@link ThreadLocalRandomSource}) ⇒ cùng seed cho cùng toàn bộ lượt
 * chạy, đến từng sự kiện ({@link InvariantCheckingSink#eventDigest()}). Trước 2026-10-06 World dùng
 * {@code ThreadLocalRandom} ngay trong test: seed 24304 đỏ ~7% số lần chạy vì không ai chết.
 *
 * <p>Đầu vào cố ý gồm cả rác mà gateway KHÔNG lọc trước: hướng {@code NaN}/vô cực/khổng lồ, mục tiêu
 * không tồn tại/chính mình/{@code Long.MIN_VALUE}, mã skill sai hệ/không tồn tại, lượng hồi máu âm và
 * cực lớn, chỉ số trang bị âm.
 */
final class WorldFuzzRun {

    static final int CHARACTER_COUNT = 6;
    private static final ComponentMapper<PositionComponent> POSITION = ComponentMapper.getFor(PositionComponent.class);
    private static final ComponentMapper<HealthComponent> HEALTH = ComponentMapper.getFor(HealthComponent.class);
    private static final double ACTION_CHANCE_PER_TICK = 0.08;
    private static final double FOCUS_CHANCE_PER_TICK = 0.06;
    private static final double REATTACH_CHANCE_PER_TICK = 0.05;
    private static final int TRACE_LINES = 80;
    private static final float[] DIRECTIONS = {
        1f, -1f, 0f, 1f, -1f, 0.5f, -0.3f, 1e30f, -1e30f, Float.NaN, Float.POSITIVE_INFINITY, -0f
    };
    private static final List<Element> ELEMENTS =
            List.of(Element.HOA, Element.THUY, Element.PHONG, Element.LOI, Element.THO, Element.NONE);
    private static final int[] HEAL_AMOUNTS = {30, 30, 1, 0, -5, 500, Integer.MAX_VALUE};
    private static final int[] BONUS_HP = {0, 50, 106, -50, -500, 2000};
    private static final List<String> SKILL_CODES =
            new ArrayList<>(SkillRegistry.BY_CODE.keySet().stream().sorted().toList());

    private static final Map<Element, List<String>> SKILLS_BY_ELEMENT = SkillRegistry.BY_CODE.values().stream()
            .sorted(Comparator.comparing(SkillDefinition::code))
            .collect(Collectors.groupingBy(
                    SkillDefinition::faction, Collectors.mapping(SkillDefinition::code, Collectors.toList())));

    static {
        SKILL_CODES.add("no_such_skill");
    }

    private final long seed;
    private final WorldFuzzScenario scenario;
    private final SplittableRandom random;
    private final InvariantCheckingSink sink = new InvariantCheckingSink();
    private final WorldFacade world;
    private final WorldStateInvariants stateInvariants;
    private final TreeSet<Long> attached = new TreeSet<>();
    private final Deque<String> trace = new ArrayDeque<>();
    private final Map<Long, Element> elements = new HashMap<>();
    private final Map<Long, Long> focus = new HashMap<>();
    private long tick;
    private int sequence;
    private long healingZoneTicks;

    WorldFuzzRun(long seed, WorldFuzzScenario scenario) {
        this.seed = seed;
        this.scenario = scenario;
        this.random = new SplittableRandom(seed);
        // Nhánh random RIÊNG cho World (tách từ seed): kết quả trận đấu theo seed, nhưng không ăn
        // vào chuỗi lệnh — thêm một lần gọi random trong server không làm lệch lệnh của bộ kiểm.
        this.world = scenario.newWorld(sink, random.split());
        sink.bind(world);
        this.stateInvariants = new WorldStateInvariants(world, scenario, sink);
    }

    /** Chạy tối đa {@code ticks} tick; dừng ngay ở tick đầu tiên có vi phạm. Trả về vi phạm (rỗng = xanh). */
    List<String> run(int ticks) {
        for (long id = 1; id <= CHARACTER_COUNT; id++) {
            attach(id);
        }
        while (tick < ticks) {
            tick++;
            sink.beginTick(tick);
            for (long id = 1; id <= CHARACTER_COUNT; id++) {
                actFor(id);
            }
            // Tick tay không có lưới catch của tick thật: exception lọt ra là ĐỎ, kèm seed + vết lệnh.
            assertThatCode(world::runOneTickOnCallerThread)
                    .as(() -> failureReport(List.of("tick " + tick + ": exception lọt khỏi tick")))
                    .doesNotThrowAnyException();
            countZoneVisits();
            stateInvariants.check(tick, attached);
            AreaOfInterestInvariant.checkNoStaleEntries(tick, world, sink);
            if (AreaOfInterestInvariant.isSyncTick(tick)) {
                AreaOfInterestInvariant.check(tick, world, sink);
            }
            if (!sink.violations().isEmpty()) {
                break;
            }
        }
        return sink.violations();
    }

    InvariantCheckingSink sink() {
        return sink;
    }

    long ticksRun() {
        return tick;
    }

    String trace() {
        return String.join("\n", trace);
    }

    /** Mô tả cho thông báo đỏ: seed để chạy lại + vết lệnh cuối. */
    String failureReport(List<String> violations) {
        return "seed=" + seed + " scenario=" + scenario + " (chạy lại: -Dkage.worldFuzz.seed=" + seed
                + " -Dkage.worldFuzz.seeds=1)\n"
                + String.join("\n", violations.subList(0, Math.min(10, violations.size())))
                + "\n--- trạng thái lúc đỏ ---\n" + stateDump()
                + "--- " + TRACE_LINES + " lệnh cuối ---\n" + trace();
    }

    private String stateDump() {
        StringBuilder text = new StringBuilder();
        dump(text, "nhân vật", world.characterEntitiesView());
        dump(text, "quái", world.monsterEntitiesView());
        for (long id : attached) {
            text.append("thấy[")
                    .append(id)
                    .append("]=")
                    .append(new TreeSet<>(sink.seenBy(id)))
                    .append('\n');
        }
        return text.toString();
    }

    private static void dump(StringBuilder text, String label, Map<Long, KEntity> entities) {
        for (Map.Entry<Long, KEntity> entry : new TreeMap<>(entities).entrySet()) {
            PositionComponent position = POSITION.get(entry.getValue());
            HealthComponent health = HEALTH.get(entry.getValue());
            text.append(label)
                    .append(' ')
                    .append(entry.getKey())
                    .append(" x=")
                    .append(position.x)
                    .append(" y=")
                    .append(position.y)
                    .append(" z=")
                    .append(position.z)
                    .append(" hp=")
                    .append(health.current)
                    .append('/')
                    .append(health.max)
                    .append('\n');
        }
    }

    /** Số tick có ít nhất một nhân vật đứng trong vùng tông môn (hồi máu) — chứng cứ phủ. */
    long healingZoneTicks() {
        return healingZoneTicks;
    }

    // ---------------------------------------------------------------------------------------------

    private void countZoneVisits() {
        for (KEntity entity : world.characterEntitiesView().values()) {
            float x = POSITION.get(entity).x;
            if (x >= WorldFuzzScenario.HEALING_ZONE_MIN_X && x <= WorldFuzzScenario.HEALING_ZONE_MAX_X) {
                healingZoneTicks++;
                return;
            }
        }
    }

    private void actFor(long id) {
        if (!attached.contains(id)) {
            maybeReturn(id);
            return;
        }
        Long focused = focus.get(id);
        if (focused != null && random.nextDouble() < FOCUS_CHANCE_PER_TICK) {
            pursue(id, focused);
            return;
        }
        if (random.nextDouble() < ACTION_CHANCE_PER_TICK) {
            actCommonly(id, random.nextInt(100));
        }
    }

    private void actCommonly(long id, int roll) {
        if (roll < 10) {
            engage(id);
        } else if (roll < 34) {
            move(id);
        } else if (roll < 40) {
            log(id, "jump");
            world.enqueueJumpCommand(id, ++sequence);
        } else if (roll < 50) {
            long target = pickTarget(id);
            log(id, "setTarget(" + target + ")");
            world.setTarget(id, target);
        } else if (roll < 70) {
            log(id, "attack");
            world.attack(id);
        } else {
            actRarely(id, roll);
        }
    }

    private void maybeReturn(long id) {
        if (random.nextDouble() < REATTACH_CHANCE_PER_TICK) {
            attach(id);
        }
    }

    private void actRarely(long id, int roll) {
        if (roll < 84) {
            String skill = SKILL_CODES.get(random.nextInt(SKILL_CODES.size()));
            long target = pickTarget(id);
            log(id, "useSkill(" + skill + ", " + target + ")");
            world.useSkill(id, skill, target);
        } else if (roll < 89) {
            int amount = HEAL_AMOUNTS[random.nextInt(HEAL_AMOUNTS.length)];
            log(id, "heal(" + amount + ")");
            world.enqueueHealCharacter(id, amount);
        } else if (roll < 92) {
            EquipmentBonusSnapshot bonus = randomBonus();
            log(id, "equip(" + bonus + ")");
            world.applyEquipmentBonus(id, bonus);
        } else if (roll < 94) {
            log(id, "chat");
            world.enqueueAreaChatMessage(id, "c" + id, "hi");
        } else if (roll < 95) {
            log(id, "appearanceChanged");
            world.enqueueAppearanceChanged(id);
        } else if (roll < 99) {
            leave(id);
        } else {
            requestBoss(id);
        }
    }

    /**
     * Chọn entity GẦN NHẤT (quái hoặc nhân vật khác) làm mục tiêu và bám theo nó — không có hành vi
     * này thì lệnh ngẫu nhiên hiếm khi đưa ai vào tầm đánh 2m, và lượt chạy "xanh" chỉ vì không ai
     * đánh ai.
     */
    private void engage(long id) {
        KEntity self = world.characterEntitiesView().get(id);
        if (self == null) {
            return; // lệnh attach còn nằm trong hàng đợi — chưa có entity để đứng.
        }
        float x = POSITION.get(self).x;
        long best = 0L;
        float bestDistance = Float.MAX_VALUE;
        for (Map<Long, KEntity> group : List.of(world.monsterEntitiesView(), world.characterEntitiesView())) {
            for (Map.Entry<Long, KEntity> entry : new TreeMap<>(group).entrySet()) {
                float distance = Math.abs(POSITION.get(entry.getValue()).x - x);
                if (entry.getKey() != id && distance < bestDistance) {
                    best = entry.getKey();
                    bestDistance = distance;
                }
            }
        }
        if (best != 0L) {
            focus.put(id, best);
            log(id, "engage(" + best + ")");
            world.setTarget(id, best);
        }
    }

    /** Đi về phía mục tiêu đang bám; tới tầm thì đánh thường hoặc dùng skill CỦA HỆ MÌNH lên nó. */
    private void pursue(long id, long targetId) {
        KEntity target = targetId > 0
                ? world.characterEntitiesView().get(targetId)
                : world.monsterEntitiesView().get(targetId);
        KEntity self = world.characterEntitiesView().get(id);
        if (target == null || self == null) {
            focus.remove(id);
            return;
        }
        float dx = POSITION.get(target).x - POSITION.get(self).x;
        if (Math.abs(dx) > CombatConstants.ATTACK_RANGE_METERS * 0.75f) {
            log(id, "pursue-move(" + Math.signum(dx) + ")");
            world.enqueueMoveCommand(id, ++sequence, Math.signum(dx), 0f);
        } else if (random.nextInt(10) < 6) {
            log(id, "pursue-attack");
            world.enqueueMoveCommand(id, ++sequence, 0f, 0f);
            world.attack(id);
        } else {
            List<String> own =
                    SKILLS_BY_ELEMENT.getOrDefault(elements.get(id), List.of(SkillConstants.FIREBALL_SKILL_CODE));
            String skill = own.get(random.nextInt(own.size()));
            log(id, "pursue-skill(" + skill + ", " + targetId + ")");
            world.useSkill(id, skill, random.nextInt(4) == 0 ? id : targetId);
        }
    }

    /**
     * Rời world — một nửa số lần ngay sau một lệnh CUỐI trong CÙNG tick (uống đan, đổi đồ, đánh,
     * skill): đúng cảnh "bấm xong rồi rớt mạng", nơi kết quả của lệnh trước còn chưa phát lúc lệnh
     * rời được xử lý.
     */
    private void leave(long id) {
        int last = random.nextInt(8);
        if (last == 0) {
            log(id, "heal(30)");
            world.enqueueHealCharacter(id, 30);
        } else if (last == 1) {
            EquipmentBonusSnapshot bonus = randomBonus();
            log(id, "equip(" + bonus + ")");
            world.applyEquipmentBonus(id, bonus);
        } else if (last == 2) {
            log(id, "attack");
            world.attack(id);
        } else if (last == 3) {
            Long focused = focus.get(id);
            pursue(id, focused == null ? pickTarget(id) : focused);
        }
        log(id, "detach");
        world.detachCharacter(id);
        attached.remove(id);
    }

    private void move(long id) {
        float dirX = DIRECTIONS[random.nextInt(DIRECTIONS.length)];
        float dirY = DIRECTIONS[random.nextInt(DIRECTIONS.length)];
        log(id, "move(" + dirX + ", " + dirY + ")");
        world.enqueueMoveCommand(id, ++sequence, dirX, dirY);
    }

    /** Boss qua {@code spawnBoss}: mã boss thật, mã không phải boss, mã không tồn tại. */
    private void requestBoss(long id) {
        String[] codes = {TestMonsterCatalogs.SAMPLE_BOSS_CODE, TestMonsterCatalogs.TRAINING_DUMMY_CODE, "no_such_boss"
        };
        String code = codes[random.nextInt(codes.length)];
        log(id, "spawnBoss(" + code + ")");
        world.spawnBoss(code);
        boolean spawns = TestMonsterCatalogs.SAMPLE_BOSS_CODE.equals(code) && scenario != WorldFuzzScenario.ARENA;
        if (spawns) {
            stateInvariants.onBossRequested();
        }
    }

    private void attach(long id) {
        float spawnX = random.nextInt(20) == 0 ? Float.NaN : (float) random.nextDouble(-25, 45);
        Element element = ELEMENTS.get(random.nextInt(ELEMENTS.size()));
        CharacterBaseStats stats = new CharacterBaseStats(
                random.nextInt(5, 40),
                random.nextInt(0, 60),
                random.nextInt(5, 40),
                random.nextInt(5, 40),
                element,
                random.nextInt(1, 61));
        if (scenario == WorldFuzzScenario.ARENA && Float.isFinite(spawnX)) {
            spawnX = spawnX / 6f; // đấu trường: đứng gần nhau để PvP thật sự xảy ra
        }
        elements.put(id, element);
        focus.remove(id);
        CharacterEntry entry = new CharacterEntry(id, spawnX, 0f, stats, randomBonus());
        log(id, "attach(" + entry + ")");
        sink.onSessionStarted(id);
        world.attachCharacter(entry);
        attached.add(id);
    }

    private EquipmentBonusSnapshot randomBonus() {
        return new EquipmentBonusSnapshot(
                random.nextInt(0, 30),
                random.nextInt(0, 30),
                random.nextInt(0, 30),
                BONUS_HP[random.nextInt(BONUS_HP.length)]);
    }

    /** Quái đang sống, nhân vật khác, chính mình, id không tồn tại, 0, cực trị. */
    private long pickTarget(long self) {
        int roll = random.nextInt(10);
        if (roll < 4) {
            List<Long> monsters =
                    new ArrayList<>(new TreeSet<>(world.monsterEntitiesView().keySet()));
            return monsters.isEmpty() ? -1L : monsters.get(random.nextInt(monsters.size()));
        }
        if (roll < 7) {
            return 1L + random.nextInt(CHARACTER_COUNT);
        }
        long[] odd = {self, 0L, 999L, -999L, Long.MIN_VALUE, Long.MAX_VALUE, -1L};
        return odd[random.nextInt(odd.length)];
    }

    private void log(long id, String command) {
        trace.addLast("t" + tick + " c" + id + " " + command);
        if (trace.size() > TRACE_LINES) {
            trace.removeFirst();
        }
    }

    /** Thống kê phủ — để chứng minh lượt chạy không "xanh rỗng" (không ai đánh ai, không ai chết). */
    String coverage() {
        Map<String, Integer> counts = sink.eventCounts();
        return scenario + " seed=" + seed + " ticks=" + tick + " healingZoneTicks=" + healingZoneTicks
                + " " + new TreeMap<>(counts)
                + " unseenRefs=" + sink.unseenReferenceCount() + " " + sink.unseenExamples();
    }
}

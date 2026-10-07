package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Bộ kiểm THUỘC TÍNH cho mô phỏng thế giới (2026-10-04): {@value WorldFuzzRun#CHARACTER_COUNT} nhân
 * vật bấm lệnh ngẫu nhiên (di chuyển kể cả hướng rác, nhảy, nhắm mục tiêu bất kỳ, đánh, skill bất kỳ,
 * uống đan, đổi trang bị, rời/vào lại world, chết/hồi sinh tự nhiên, ra vào vùng an toàn) lần lượt
 * trong ba loại World ({@link WorldFuzzScenario}); World tick TAY, bất biến kiểm sau MỖI tick:
 *
 * <ul>
 *   <li>Toạ độ hữu hạn; nhân vật trên đường chơi {@code y=0}, {@code 0 ≤ z ≤ 6}, không bước nhanh hơn
 *       tốc độ tối đa; quái trong vòng xích quanh điểm spawn ({@link WorldStateInvariants}).
 *   <li>Máu trong {@code [0, max]}, khí trong {@code [0, max]}, cooldown trong {@code [0, chu kỳ]}.
 *   <li>Xác (0 máu từ tick trước) không ra đòn, không trượt ngang; world chính không để ai nằm 0 máu.
 *   <li>Không entity mồ côi: {@code EcsWorld.entityCount()} = nhân vật + quái + NPC; nhân vật trong world
 *       đúng bằng tập đang gắn; quái sống ≤ điểm spawn + boss đã gọi.
 *   <li>AoI khớp khoảng cách sau mỗi lần sync ({@link AreaOfInterestInvariant}).
 *   <li>Mọi sự kiện: người nhận đang gắn, không nhắc entity đã despawn với người đó, spawn/despawn
 *       không lặp ({@link InvariantCheckingSink}).
 *   <li>Không exception nào lọt khỏi tick — tick tay không có lưới {@code catch} của tick thật.
 * </ul>
 *
 * <p>Ngân sách mặc định vừa cổng {@code check}. Bản dài: {@code -Dkage.worldFuzz.seeds=60
 * -Dkage.worldFuzz.ticks=36000 -Djunit.jupiter.execution.timeout.mode=disabled}. Đỏ thì thông báo in
 * seed + vết lệnh; chạy lại đúng seed đó bằng {@code -Dkage.worldFuzz.seed=<seed> -Dkage.worldFuzz.seeds=1}.
 */
class WorldSimulationPropertyTest {

    private static final long BASE_SEED = Long.getLong("kage.worldFuzz.seed", 0x5EEDL);
    private static final int SEEDS = Integer.getInteger("kage.worldFuzz.seeds", 24);
    /** 7200 tick = 2 phút mô phỏng mỗi seed (24 seed ≈ 15s trên máy dev). */
    private static final int TICKS = Integer.getInteger("kage.worldFuzz.ticks", 7200);
    /** Seed hầm ngục từng đỏ ngẫu nhiên (thiếu {@code characterDefeated}) khi World chưa nhận random seed. */
    private static final long DETERMINISM_FLAKE_SEED = 24_304L;

    @Test
    @Timeout(120)
    void shouldHoldEveryWorldInvariant_underRandomisedPlayerCommands() {
        for (int i = 0; i < SEEDS; i++) {
            long seed = BASE_SEED + i;
            WorldFuzzScenario scenario = scenarioOf(seed);
            WorldFuzzRun run = new WorldFuzzRun(seed, scenario);
            List<String> violations = run.run(TICKS);
            assertThat(violations).as(() -> run.failureReport(violations)).isEmpty();
            System.out.println("[worldFuzz] " + run.coverage());
            assertThat(run.sink().eventCounts())
                    .as("lượt chạy phải thật sự có giao tranh: " + run.coverage())
                    .containsKeys("attackResult", "characterSpawned", "characterDefeated", "moveAccepted");
            if (scenario == WorldFuzzScenario.OVERWORLD) {
                assertThat(run.sink().eventCounts())
                        .as("world chính phải có quái chết và người hồi sinh: " + run.coverage())
                        .containsKeys("monsterKilled", "characterRespawned", "npcSpawned");
                assertThat(run.healingZoneTicks())
                        .as("phải có người bước vào vùng tông môn")
                        .isPositive();
            }
        }
    }

    /**
     * Cùng seed ⇒ cùng lượt chạy đến từng sự kiện. Trước 2026-10-06 World dùng {@code ThreadLocalRandom}
     * ngay trong bộ kiểm: seed 24304 (hầm ngục) đỏ ~7% số lần chạy vì lượt đó không ai chết, chạy lại
     * thì xanh — một seed "cố định" không tái hiện được thì thông báo "chạy lại đúng seed này" là vô
     * dụng. Ca này giữ cho mọi nguồn bất định (đồng hồ, luồng, thứ tự băm theo identity, random không
     * seed) không lọt lại vào mô phỏng hay bộ kiểm.
     */
    @Test
    @Timeout(120)
    void shouldReplayIdentically_whenTheSameSeedRunsTwice() {
        long[] seeds = {DETERMINISM_FLAKE_SEED, BASE_SEED + 1, BASE_SEED + 2}; // hầm ngục, đấu trường, world chính
        for (long seed : seeds) {
            WorldFuzzScenario scenario = scenarioOf(seed);
            WorldFuzzRun first = new WorldFuzzRun(seed, scenario);
            WorldFuzzRun second = new WorldFuzzRun(seed, scenario);
            List<String> firstViolations = first.run(TICKS);
            List<String> secondViolations = second.run(TICKS);

            assertThat(second.sink().eventTotal())
                    .as("số sự kiện, seed " + seed + " " + scenario)
                    .isEqualTo(first.sink().eventTotal())
                    .isPositive();
            assertThat(second.sink().eventDigest())
                    .as("dấu vân tay luồng sự kiện, seed " + seed + " " + scenario)
                    .isEqualTo(first.sink().eventDigest());
            assertThat(second.coverage()).isEqualTo(first.coverage());
            assertThat(second.trace()).isEqualTo(first.trace());
            assertThat(secondViolations).isEqualTo(firstViolations);
        }
    }

    private static WorldFuzzScenario scenarioOf(long seed) {
        WorldFuzzScenario[] scenarios = WorldFuzzScenario.values();
        return scenarios[(int) Math.floorMod(seed, (long) scenarios.length)];
    }
}

package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.protocol.Faction;
import dev.kage.protocol.MonsterSpawnedEvent;

/**
 * Từ 2026-10-02 server-gateway giới hạn {@code SetTarget}/{@code Attack}/{@code UseSkill} CHUNG một
 * trần {@value #SERVER_COMBAT_LIMIT_PER_SECOND} lệnh/giây mỗi kết nối
 * ({@code GatewayChannelHandler.MAX_COMBAT_COMMANDS_PER_SECOND}): lệnh vượt trần bị bỏ IM LẶNG, ba
 * cửa sổ vượt liên tiếp thì bị ngắt kết nối. Client có một đường TỰ gửi lặp — cửa sổ chờ xác nhận
 * của {@link AttackInputController} (mỗi 100ms) — nên câu hỏi "người chơi thật có chạm trần không"
 * phải được trả lời bằng số đếm, không bằng cảm giác.
 *
 * <p>Chạy đúng các bộ điều khiển thật ({@link AttackInputController} cho đánh thường,
 * {@link CombatHudController} cho cổng hồi chiêu của skill và cổng "đổi mục tiêu mới gửi") ở nhịp
 * 60Hz thật, với server KHÔNG BAO GIỜ xác nhận (trường hợp xấu nhất: luôn ngoài tầm, cửa sổ gửi lại
 * chạy hết cỡ). {@code LocalIntentSender} — lớp nối ba nguồn đó vào {@code WorldCommandSender} —
 * cần ngữ cảnh GL nên không dựng được ở đây; vòng lặp {@link #simulate} chép đúng thứ tự và điều
 * kiện của nó ({@code updateAttack} rồi {@code updateSkill}).
 *
 * <p>Đếm theo cửa sổ TRƯỢT 60 tick: mọi cửa sổ cố định 1 giây mà server có thể chọn đều nằm trong
 * một cửa sổ trượt như thế, nên trần đo ở đây là trần thật.
 */
class CombatCommandRateTest {

    private static final float TICK = 1f / 60f;
    private static final int TICKS_PER_SECOND = 60;
    private static final int SIMULATED_SECONDS = 6;

    /** Bản sao của {@code GatewayChannelHandler.MAX_COMBAT_COMMANDS_PER_SECOND}. */
    private static final int SERVER_COMBAT_LIMIT_PER_SECOND = 30;

    /**
     * Nhịp bấm nhanh nhất coi là của người thật cho MỘT ngón/một tay: 15 lần/giây (4 tick). Kỷ lục
     * bấm chuột của con người quanh 14–16 lần/giây trong vài giây; nhanh hơn là macro.
     */
    private static final int FASTEST_HUMAN_PERIOD_TICKS = 4;

    private static final long SELF = 42L;
    private static final long MONSTER_A = -100L;
    private static final long MONSTER_B = -101L;

    /** Một kịch bản đầu vào: mỗi tick, người chơi đang làm gì. */
    private interface Input {
        boolean attackKeyHeld(int tick);

        boolean attackButtonClicked(int tick);

        boolean skillKeyHeld(int tick);

        /** Monster được click chọn ở tick này, {@code 0} = không click. */
        long targetClicked(int tick);
    }

    @Test
    void holdingTheAttackKey_neverConfirmed_staysFarBelowTheServerLimit() {
        int peak = simulate(new Input() {
            @Override
            public boolean attackKeyHeld(int tick) {
                return true;
            }

            @Override
            public boolean attackButtonClicked(int tick) {
                return false;
            }

            @Override
            public boolean skillKeyHeld(int tick) {
                return false;
            }

            @Override
            public long targetClicked(int tick) {
                return 0L;
            }
        });

        // Một INTENT mỗi nhịp lặp + gửi lại mỗi 100ms trong cửa sổ 1 giây.
        assertThat(peak).isLessThanOrEqualTo(11);
    }

    @Test
    void mashingTheAttackButton_atAnyHumanRate_staysBelowTheServerLimit() {
        for (int period = FASTEST_HUMAN_PERIOD_TICKS; period <= TICKS_PER_SECOND; period++) {
            int clickPeriod = period;
            int peak = simulate(new Input() {
                @Override
                public boolean attackKeyHeld(int tick) {
                    return false;
                }

                @Override
                public boolean attackButtonClicked(int tick) {
                    return tick % clickPeriod == 0;
                }

                @Override
                public boolean skillKeyHeld(int tick) {
                    return false;
                }

                @Override
                public long targetClicked(int tick) {
                    return 0L;
                }
            });
            // Click nhanh hơn 100ms thì mỗi click tự đóng lần gửi lại của click trước; chậm hơn thì
            // mỗi click kéo theo đúng một lần gửi lại ⇒ trần ~2 lệnh mỗi 100ms+ = dưới 20/giây.
            assertThat(peak).as("click mỗi %d tick", clickPeriod).isLessThanOrEqualTo(20);
        }
    }

    /**
     * Mọi thứ cùng lúc, mỗi nguồn ở nhịp người nhanh nhất: tay trái gõ phím đánh 10 lần/giây và
     * phím skill 10 lần/giây, tay phải click nút Đánh 15 lần/giây — hoặc click đổi qua lại giữa hai
     * con quái 15 lần/giây ({@code SetTargetCommand}). Đây là cận trên của một người thật, không
     * phải lối chơi thường gặp.
     */
    @Test
    void everyCombatInputAtOnce_atHumanRates_staysBelowTheServerLimit() {
        int withButton = simulate(twoHandedMash(false));
        int withTargetSwitching = simulate(twoHandedMash(true));

        assertThat(withButton).isLessThan(SERVER_COMBAT_LIMIT_PER_SECOND);
        assertThat(withTargetSwitching).isLessThan(SERVER_COMBAT_LIMIT_PER_SECOND);
    }

    private static Input twoHandedMash(boolean mouseSwitchesTargets) {
        return new Input() {
            @Override
            public boolean attackKeyHeld(int tick) {
                return tick % 6 < 3; // 10 lần/giây, giữ 50ms mỗi lần
            }

            @Override
            public boolean attackButtonClicked(int tick) {
                return !mouseSwitchesTargets && tick % FASTEST_HUMAN_PERIOD_TICKS == 1;
            }

            @Override
            public boolean skillKeyHeld(int tick) {
                return (tick + 3) % 6 < 3; // xen kẽ với phím đánh, 10 lần/giây
            }

            @Override
            public long targetClicked(int tick) {
                if (!mouseSwitchesTargets || tick % FASTEST_HUMAN_PERIOD_TICKS != 1) {
                    return 0L;
                }
                return (tick / FASTEST_HUMAN_PERIOD_TICKS) % 2 == 0 ? MONSTER_A : MONSTER_B;
            }
        };
    }

    /**
     * Chạy {@link #SIMULATED_SECONDS} giây và trả về số lệnh chiến đấu lớn nhất trong một cửa sổ
     * trượt 1 giây.
     */
    private static int simulate(Input input) {
        AttackInputController attack = new AttackInputController(SELF);
        CombatHudController combat = new CombatHudController(SELF, Faction.FACTION_HOA);
        combat.onMonsterSpawned(monster(MONSTER_A, 3f));
        combat.onMonsterSpawned(monster(MONSTER_B, 4f));

        int totalTicks = SIMULATED_SECONDS * TICKS_PER_SECOND;
        int[] sentAt = new int[totalTicks];
        boolean skillKeyWasHeld = false;
        for (int tick = 0; tick < totalTicks; tick++) {
            int sent = 0;
            long clicked = input.targetClicked(tick);
            if (clicked != 0L && combat.selectTarget(clicked)) {
                sent++; // SetTargetCommand — WorldScreen.selectTargetAt
            }
            if (input.attackButtonClicked(tick)) {
                attack.onButtonClicked();
            }
            // Thứ tự của LocalIntentSender.tick: updateAttack rồi updateSkill.
            if (attack.update(input.attackKeyHeld(tick), TICK, combat.currentTargetId())
                    != AttackInputController.Send.NONE) {
                sent++; // AttackCommand
            }
            boolean skillHeld = input.skillKeyHeld(tick);
            if (skillHeld && !skillKeyWasHeld && combat.useSkill()) {
                sent++; // UseSkillCommand
            }
            skillKeyWasHeld = skillHeld;
            combat.tick(TICK);
            sentAt[tick] = sent;
        }
        return peakPerSecond(sentAt);
    }

    private static int peakPerSecond(int[] sentAt) {
        int window = 0;
        int peak = 0;
        for (int tick = 0; tick < sentAt.length; tick++) {
            window += sentAt[tick];
            if (tick >= TICKS_PER_SECOND) {
                window -= sentAt[tick - TICKS_PER_SECOND];
            }
            peak = Math.max(peak, window);
        }
        return peak;
    }

    private static MonsterSpawnedEvent monster(long id, float x) {
        return MonsterSpawnedEvent.newBuilder()
                .setMonsterId(id)
                .setMonsterCode("training_dummy")
                .setX(x)
                .setY(0f)
                .setCurrentHp(50)
                .setMaxHp(50)
                .build();
    }
}

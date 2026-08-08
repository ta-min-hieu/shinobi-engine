package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.protocol.AttackResultEvent;
import dev.kage.protocol.MonsterDespawnedEvent;
import dev.kage.protocol.MonsterMovedEvent;
import dev.kage.protocol.MonsterSpawnedEvent;
import dev.kage.protocol.SkillResultEvent;

class CombatHudControllerTest {

    private static final long LOCAL_CHARACTER_ID = 7L;
    private static final long MONSTER_ID = -1L;

    private static MonsterSpawnedEvent dummySpawned() {
        return MonsterSpawnedEvent.newBuilder()
                .setMonsterId(MONSTER_ID)
                .setMonsterCode("training_dummy")
                .setX(5f)
                .setY(5f)
                .setCurrentHp(50)
                .setMaxHp(50)
                .build();
    }

    @Test
    void onMonsterSpawned_autoTargetsWhenNoCurrentTarget() {
        CombatHudController controller = new CombatHudController(LOCAL_CHARACTER_ID);

        long autoTargeted = controller.onMonsterSpawned(dummySpawned());

        assertThat(autoTargeted).isEqualTo(MONSTER_ID);
        assertThat(controller.currentTargetId()).isEqualTo(MONSTER_ID);
        assertThat(controller.monsters()).containsKey(MONSTER_ID);
    }

    @Test
    void onMonsterSpawned_doesNotRetargetWhenTargetAlreadySet() {
        CombatHudController controller = new CombatHudController(LOCAL_CHARACTER_ID);
        controller.onMonsterSpawned(dummySpawned());

        long secondMonsterId = -2L;
        long autoTargeted = controller.onMonsterSpawned(MonsterSpawnedEvent.newBuilder()
                .setMonsterId(secondMonsterId)
                .setMonsterCode("training_dummy")
                .setX(6f)
                .setY(5f)
                .setCurrentHp(50)
                .setMaxHp(50)
                .build());

        assertThat(autoTargeted).isZero();
        assertThat(controller.currentTargetId()).isEqualTo(MONSTER_ID);
    }

    @Test
    void canUseSkill_falseWithoutTarget() {
        CombatHudController controller = new CombatHudController(LOCAL_CHARACTER_ID);

        assertThat(controller.canUseSkill()).isFalse();
    }

    @Test
    void useSkill_startsCooldownAndSpendsChi_thenBlocksUntilCooldownElapses() {
        CombatHudController controller = new CombatHudController(LOCAL_CHARACTER_ID);
        controller.onMonsterSpawned(dummySpawned());

        assertThat(controller.canUseSkill()).isTrue();
        assertThat(controller.useSkill()).isTrue();
        assertThat(controller.skillCooldownRemaining()).isEqualTo(CombatConstants.FIREBALL_COOLDOWN_SECONDS);
        assertThat(controller.canUseSkill()).isFalse(); // đang hồi chiêu
        assertThat(controller.useSkill()).isFalse(); // gọi lại khi chưa đủ điều kiện — không đổi gì thêm

        controller.tick(CombatConstants.FIREBALL_COOLDOWN_SECONDS);

        assertThat(controller.skillCooldownRemaining()).isZero();
        assertThat(controller.canUseSkill()).isTrue();
    }

    @Test
    void useSkill_blockedOnceChiExhausted() {
        CombatHudController controller = new CombatHudController(LOCAL_CHARACTER_ID);
        controller.onMonsterSpawned(dummySpawned());
        int maxUses = CombatConstants.CHARACTER_MAX_CHI / CombatConstants.FIREBALL_CHI_COST;

        for (int i = 0; i < maxUses; i++) {
            assertThat(controller.useSkill()).isTrue();
            controller.tick(CombatConstants.FIREBALL_COOLDOWN_SECONDS);
        }

        assertThat(controller.canUseSkill()).isFalse(); // đủ hồi chiêu nhưng hết Khí
        assertThat(controller.useSkill()).isFalse();
    }

    @Test
    void onAttackResult_targetingSelf_updatesPlayerHp() {
        CombatHudController controller = new CombatHudController(LOCAL_CHARACTER_ID);

        controller.onAttackResult(AttackResultEvent.newBuilder()
                .setAttackerEntityId(MONSTER_ID)
                .setTargetEntityId(LOCAL_CHARACTER_ID)
                .setDamage(15)
                .setTargetCurrentHp(85)
                .setTargetDied(false)
                .build());

        assertThat(controller.playerHp()).isEqualTo(85);
    }

    @Test
    void onAttackResult_targetingTrackedMonster_updatesItsHp() {
        CombatHudController controller = new CombatHudController(LOCAL_CHARACTER_ID);
        controller.onMonsterSpawned(dummySpawned());

        controller.onAttackResult(AttackResultEvent.newBuilder()
                .setAttackerEntityId(LOCAL_CHARACTER_ID)
                .setTargetEntityId(MONSTER_ID)
                .setDamage(20)
                .setTargetCurrentHp(30)
                .setTargetDied(false)
                .build());

        assertThat(controller.monsters().get(MONSTER_ID).hp()).isEqualTo(30);
        assertThat(controller.currentTargetId()).isEqualTo(MONSTER_ID); // vẫn còn sống, giữ mục tiêu
    }

    @Test
    void onSkillResult_killingCurrentTarget_removesMonsterAndClearsTarget() {
        CombatHudController controller = new CombatHudController(LOCAL_CHARACTER_ID);
        controller.onMonsterSpawned(dummySpawned());

        controller.onSkillResult(SkillResultEvent.newBuilder()
                .setSkillCode(CombatConstants.FIREBALL_SKILL_CODE)
                .setAttackerEntityId(LOCAL_CHARACTER_ID)
                .setTargetEntityId(MONSTER_ID)
                .setDamage(50)
                .setTargetCurrentHp(0)
                .setTargetDied(true)
                .build());

        assertThat(controller.monsters()).doesNotContainKey(MONSTER_ID);
        assertThat(controller.currentTargetId()).isZero();
        assertThat(controller.canUseSkill()).isFalse(); // hết mục tiêu
    }

    @Test
    void onMonsterDespawned_removesMonsterAndClearsTargetIfCurrent() {
        CombatHudController controller = new CombatHudController(LOCAL_CHARACTER_ID);
        controller.onMonsterSpawned(dummySpawned());

        controller.onMonsterDespawned(
                MonsterDespawnedEvent.newBuilder().setMonsterId(MONSTER_ID).build());

        assertThat(controller.monsters()).isEmpty();
        assertThat(controller.currentTargetId()).isZero();
    }

    @Test
    void onMonsterMoved_updatesPositionButKeepsHp() {
        CombatHudController controller = new CombatHudController(LOCAL_CHARACTER_ID);
        controller.onMonsterSpawned(dummySpawned());

        controller.onMonsterMoved(MonsterMovedEvent.newBuilder()
                .setMonsterId(MONSTER_ID)
                .setX(8f)
                .setY(9f)
                .build());

        CombatHudController.MonsterState state = controller.monsters().get(MONSTER_ID);
        assertThat(state.x()).isEqualTo(8f);
        assertThat(state.y()).isEqualTo(9f);
        assertThat(state.hp()).isEqualTo(50);
    }

    @Test
    void hpUpdate_forUnrelatedEntity_isIgnoredSafely() {
        CombatHudController controller = new CombatHudController(LOCAL_CHARACTER_ID);

        controller.onAttackResult(AttackResultEvent.newBuilder()
                .setAttackerEntityId(999L)
                .setTargetEntityId(999L) // nhân vật khác, không phải mình, không phải quái đang theo dõi
                .setDamage(5)
                .setTargetCurrentHp(95)
                .setTargetDied(false)
                .build());

        assertThat(controller.playerHp()).isEqualTo(controller.playerMaxHp());
        assertThat(controller.monsters()).isEmpty();
    }
}

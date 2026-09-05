package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import dev.kage.protocol.AttackResultEvent;
import dev.kage.protocol.Faction;
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
        CombatHudController controller = new CombatHudController(LOCAL_CHARACTER_ID, Faction.FACTION_HOA);

        long autoTargeted = controller.onMonsterSpawned(dummySpawned());

        assertThat(autoTargeted).isEqualTo(MONSTER_ID);
        assertThat(controller.currentTargetId()).isEqualTo(MONSTER_ID);
        assertThat(controller.monsters()).containsKey(MONSTER_ID);
    }

    @Test
    void onMonsterSpawned_doesNotRetargetWhenTargetAlreadySet() {
        CombatHudController controller = new CombatHudController(LOCAL_CHARACTER_ID, Faction.FACTION_HOA);
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

    /** Click chuột chọn mục tiêu (2026-08-09) — phải GHI ĐÈ được con tự chọn lúc spawn. */
    @Test
    void selectTarget_overridesAutoTarget_andReportsWhetherCommandIsNeeded() {
        CombatHudController controller = new CombatHudController(LOCAL_CHARACTER_ID, Faction.FACTION_HOA);
        controller.onMonsterSpawned(dummySpawned());
        long secondMonsterId = -2L;
        controller.onMonsterSpawned(MonsterSpawnedEvent.newBuilder()
                .setMonsterId(secondMonsterId)
                .setMonsterCode("training_dummy")
                .setX(6f)
                .setY(5f)
                .setCurrentHp(50)
                .setMaxHp(50)
                .build());

        assertThat(controller.selectTarget(secondMonsterId)).isTrue();
        assertThat(controller.currentTargetId()).isEqualTo(secondMonsterId);
        // Chọn lại đúng con đang nhắm — không gửi gói thừa.
        assertThat(controller.selectTarget(secondMonsterId)).isFalse();
    }

    @Test
    void selectTarget_ignoresUnknownMonster() {
        CombatHudController controller = new CombatHudController(LOCAL_CHARACTER_ID, Faction.FACTION_HOA);
        controller.onMonsterSpawned(dummySpawned());

        assertThat(controller.selectTarget(-999L)).isFalse();
        assertThat(controller.currentTargetId()).isEqualTo(MONSTER_ID);
    }

    @Test
    void canUseSkill_falseWithoutTarget() {
        CombatHudController controller = new CombatHudController(LOCAL_CHARACTER_ID, Faction.FACTION_HOA);

        assertThat(controller.canUseSkill()).isFalse();
    }

    @Test
    void useSkill_startsCooldownAndSpendsChi_thenBlocksUntilCooldownElapses() {
        CombatHudController controller = new CombatHudController(LOCAL_CHARACTER_ID, Faction.FACTION_HOA);
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
        CombatHudController controller = new CombatHudController(LOCAL_CHARACTER_ID, Faction.FACTION_HOA);
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
        CombatHudController controller = new CombatHudController(LOCAL_CHARACTER_ID, Faction.FACTION_HOA);

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
        CombatHudController controller = new CombatHudController(LOCAL_CHARACTER_ID, Faction.FACTION_HOA);
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
        CombatHudController controller = new CombatHudController(LOCAL_CHARACTER_ID, Faction.FACTION_HOA);
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
        CombatHudController controller = new CombatHudController(LOCAL_CHARACTER_ID, Faction.FACTION_HOA);
        controller.onMonsterSpawned(dummySpawned());

        controller.onMonsterDespawned(
                MonsterDespawnedEvent.newBuilder().setMonsterId(MONSTER_ID).build());

        assertThat(controller.monsters()).isEmpty();
        assertThat(controller.currentTargetId()).isZero();
    }

    @Test
    void onMonsterMoved_updatesPositionButKeepsHp() {
        CombatHudController controller = new CombatHudController(LOCAL_CHARACTER_ID, Faction.FACTION_HOA);
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
        CombatHudController controller = new CombatHudController(LOCAL_CHARACTER_ID, Faction.FACTION_HOA);

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

    @Test
    void nonHoaFaction_resolvesItsOwnSlot0Skill_notFireball() {
        CombatHudController controller = new CombatHudController(LOCAL_CHARACTER_ID, Faction.FACTION_THUY);

        assertThat(controller.skillCode()).isEqualTo("thuy_song_du");
        assertThat(controller.skillNameKey()).isEqualTo("hud.skill.thuy_song_du");
        assertThat(controller.skillCode()).isNotEqualTo(CombatConstants.FIREBALL_SKILL_CODE);
    }

    @Test
    void useSkill_forNonHoaFaction_spendsThatFactionsCooldownAndChi_notFireballs() {
        CombatHudController controller = new CombatHudController(LOCAL_CHARACTER_ID, Faction.FACTION_THUY);
        controller.onMonsterSpawned(dummySpawned());
        CombatConstants.FactionSkill thuySkill = CombatConstants.FACTION_SKILLS.get(Faction.FACTION_THUY);

        assertThat(controller.useSkill()).isTrue();

        assertThat(controller.skillCooldownRemaining()).isEqualTo(thuySkill.cooldownSeconds());
        assertThat(controller.skillCooldownRemaining()).isNotEqualTo(CombatConstants.FIREBALL_COOLDOWN_SECONDS);
    }

    @Test
    void useSkill_forNonHoaFaction_blockedOnceThatFactionsChiExhausted() {
        CombatHudController controller = new CombatHudController(LOCAL_CHARACTER_ID, Faction.FACTION_THUY);
        controller.onMonsterSpawned(dummySpawned());
        CombatConstants.FactionSkill thuySkill = CombatConstants.FACTION_SKILLS.get(Faction.FACTION_THUY);
        int maxUses = CombatConstants.CHARACTER_MAX_CHI / thuySkill.chiCost();

        for (int i = 0; i < maxUses; i++) {
            assertThat(controller.useSkill()).isTrue();
            controller.tick(thuySkill.cooldownSeconds());
        }

        assertThat(controller.canUseSkill()).isFalse(); // đủ hồi chiêu nhưng hết Khí
        assertThat(controller.useSkill()).isFalse();
    }

    @Test
    void onRespawn_resetsHpChiAndClearsTarget() {
        CombatHudController controller = new CombatHudController(LOCAL_CHARACTER_ID, Faction.FACTION_HOA);
        controller.onMonsterSpawned(dummySpawned());
        controller.useSkill(); // tốn Khí — để onRespawn có gì đó THẬT phải nạp lại.
        controller.onAttackResult(AttackResultEvent.newBuilder()
                .setAttackerEntityId(MONSTER_ID)
                .setTargetEntityId(LOCAL_CHARACTER_ID)
                .setDamage(100)
                .setTargetCurrentHp(0)
                .setTargetDied(true)
                .build());
        assertThat(controller.playerHp()).isZero();

        controller.onRespawn(
                CombatConstants.CHARACTER_MAX_HP, CombatConstants.CHARACTER_MAX_HP, CombatConstants.CHARACTER_MAX_CHI);

        assertThat(controller.playerHp()).isEqualTo(CombatConstants.CHARACTER_MAX_HP);
        assertThat(controller.currentTargetId()).isZero();
        assertThat(controller.canUseSkill()).isFalse(); // hết mục tiêu sau khi hồi sinh

        // Xác nhận Khí THẬT được nạp lại (không chỉ HP) — chờ hết hồi chiêu cũ (onRespawn KHÔNG
        // reset cooldown, chỉ HP/Khí/mục tiêu), chọn lại mục tiêu rồi dùng skill lại được ngay dù
        // vừa tốn Khí trước lúc chết.
        controller.tick(CombatConstants.FIREBALL_COOLDOWN_SECONDS);
        controller.onMonsterSpawned(MonsterSpawnedEvent.newBuilder()
                .setMonsterId(-2L)
                .setMonsterCode("training_dummy")
                .setX(5f)
                .setY(5f)
                .setCurrentHp(50)
                .setMaxHp(50)
                .build());
        assertThat(controller.useSkill()).isTrue();
    }

    @Test
    void constructor_rejectsFactionWithoutASlot0SkillMapping() {
        assertThatThrownBy(() -> new CombatHudController(LOCAL_CHARACTER_ID, Faction.FACTION_UNSPECIFIED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldTakeMaxHpFromServer_notFromClientConstant() {
        // ADR-036: bonus_hp của trang bị nâng MÁU TỐI ĐA, nên mẫu số vạch máu không còn là hằng số
        // phía client. CharacterRespawnedEvent vốn ĐÃ mang max_hp (movement.proto field 4) —
        // trước đây client nhận rồi vứt đi.
        CombatHudController controller = new CombatHudController(LOCAL_CHARACTER_ID, Faction.FACTION_HOA);
        assertThat(controller.playerMaxHp()).isEqualTo(CombatConstants.CHARACTER_MAX_HP);

        controller.onRespawn(158, 158, CombatConstants.CHARACTER_MAX_CHI);

        assertThat(controller.playerMaxHp()).isEqualTo(158);
        assertThat(controller.playerHp()).isEqualTo(158);
    }

    @Test
    void shouldKeepKnownMaxHp_whenServerSendsZero() {
        // Server CŨ chưa gửi field này ⇒ 0 (mặc định proto3). Lấy 0 làm máu tối đa là chia cho 0 ở
        // vạch máu — giữ giá trị đang biết thay vì tin một con số rỗng.
        CombatHudController controller = new CombatHudController(LOCAL_CHARACTER_ID, Faction.FACTION_HOA);

        controller.onRespawn(100, 0, CombatConstants.CHARACTER_MAX_CHI);

        assertThat(controller.playerMaxHp()).isEqualTo(CombatConstants.CHARACTER_MAX_HP);
    }

    @Test
    void shouldTakeMaxHpFromEnterWorld_beforeAnyEventArrives() {
        // ADR-036 hậu kiểm (playtest 2026-08-30): trước khi SelectCharacterResponse mang max_hp,
        // mẫu số vạch máu kẹt ở CHARACTER_MAX_HP trong khi tử số là số THẬT của server — người chơi
        // mặc đồ +HP nhìn thấy "139 / 100", thanh máu tràn, cho tới lần hồi sinh đầu tiên.
        CombatHudController controller = new CombatHudController(LOCAL_CHARACTER_ID, Faction.FACTION_HOA);

        controller.onEnterWorld(245, 245);

        assertThat(controller.playerMaxHp()).isEqualTo(245);
        assertThat(controller.playerHp()).isEqualTo(245);
    }

    @Test
    void shouldIgnoreEnterWorldVitals_whenServerSendsZeroMaxHp() {
        // Server CŨ chưa có field (mặc định proto3) — giữ phỏng đoán đang có thay vì chia cho 0.
        CombatHudController controller = new CombatHudController(LOCAL_CHARACTER_ID, Faction.FACTION_HOA);

        controller.onEnterWorld(0, 0);

        assertThat(controller.playerMaxHp()).isEqualTo(CombatConstants.CHARACTER_MAX_HP);
        assertThat(controller.playerHp()).isEqualTo(CombatConstants.CHARACTER_MAX_HP);
    }

    /**
     * ADR-043 — nửa SAU của bug ADR-036. Mặc đồ giữa world đổi máu tối đa thật ở server ngay lập
     * tức, nhưng trước ADR-043 không gói nào chở con số đó về, nên vạch máu kẹt ở giá trị lúc VÀO
     * WORLD cho tới lần hồi sinh đầu tiên: playtest 2026-09-05 mặc trọn bộ +18 máu mà HUD vẫn ghi
     * "100 / 100".
     */
    @Test
    void shouldTakeNewMaxHp_whenEquipmentChangesMidWorld() {
        CombatHudController controller = new CombatHudController(LOCAL_CHARACTER_ID, Faction.FACTION_HOA);
        controller.onEnterWorld(100, 100);

        controller.onOwnMaxHpChanged(118);

        assertThat(controller.playerMaxHp()).isEqualTo(118);
        // Tử số KHÔNG tự nhảy theo: server dịch nó theo độ chênh nhưng gateway không đọc được máu
        // hiện tại (ADR-043 D5 phương án (a)) — client giữ số của mình tới gói mang HP tiếp theo.
        assertThat(controller.playerHp()).isEqualTo(100);
    }

    @Test
    void shouldClampCurrentHp_whenUnequippingShrinksMaxHp() {
        // Gỡ đồ +HP: mẫu số tụt xuống dưới tử số đang có ⇒ vạch máu sẽ TRÀN ra ngoài khung nếu
        // không cắt. Cùng họ lỗi hiển thị "139 / 100" mà ADR-036 đã gặp một lần.
        CombatHudController controller = new CombatHudController(LOCAL_CHARACTER_ID, Faction.FACTION_HOA);
        controller.onEnterWorld(118, 118);

        controller.onOwnMaxHpChanged(100);

        assertThat(controller.playerMaxHp()).isEqualTo(100);
        assertThat(controller.playerHp()).isEqualTo(100);
    }

    @Test
    void shouldIgnoreMaxHpChange_whenServerSendsZero() {
        // 0 = thao tác THẤT BẠI (hoặc server cũ chưa có field) — giữ nguyên, không chia cho 0.
        CombatHudController controller = new CombatHudController(LOCAL_CHARACTER_ID, Faction.FACTION_HOA);
        controller.onEnterWorld(118, 118);

        controller.onOwnMaxHpChanged(0);

        assertThat(controller.playerMaxHp()).isEqualTo(118);
        assertThat(controller.playerHp()).isEqualTo(118);
    }
}

package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

/**
 * ADR-126 + lô 24 art — mỗi đợt lôi kích phát một {@code SkillResultEvent} mã {@code thien_kiep} tới người độ kiếp,
 * để client phát {@code vfx_thien_kiep_impact} tại chỗ họ đứng (đường phát VFX có sẵn, không gói mới).
 */
class TribulationStrikeVfxTest {

    private static final long CHARACTER_ID = 7L;
    private static final CharacterBaseStats STATS = new CharacterBaseStats(10, 10, 10, 10, Element.HOA, 18);

    @SuppressWarnings({"ReferenceEquality", "PMD.CompareObjectsWithEquals"})
    private static WorldOutboundSink recording(List<WorldOutboundSink.SkillOutcome> skills, List<Long> targets) {
        return (WorldOutboundSink) Proxy.newProxyInstance(
                WorldOutboundSink.class.getClassLoader(),
                new Class<?>[] {WorldOutboundSink.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "onSkillResult" -> {
                        targets.add((Long) args[0]);
                        skills.add((WorldOutboundSink.SkillOutcome) args[1]);
                        yield null;
                    }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "RecordingSink";
                    default -> null;
                });
    }

    @Test
    void shouldSendTheTribulationStrikeAsASkillResultToTheCharacterStruck() {
        List<WorldOutboundSink.SkillOutcome> skills = new CopyOnWriteArrayList<>();
        List<Long> targets = new CopyOnWriteArrayList<>();
        WorldFacade world = new WorldFacade(recording(skills, targets), false);
        world.attachCharacter(CHARACTER_ID, 0f, 0f, STATS);
        world.runOneTickOnCallerThread();
        int maxHp = WorldFacade.characterMaxHp(STATS.level(), EquipmentBonusSnapshot.NONE);

        world.strikeCharacter(CHARACTER_ID, 20);
        world.runOneTickOnCallerThread();

        assertThat(skills).hasSize(1);
        WorldOutboundSink.SkillOutcome strike = skills.getFirst();
        assertThat(strike.skillCode())
                .isEqualTo(WorldFacade.TRIBULATION_STRIKE_CODE)
                .isEqualTo("thien_kiep");
        assertThat(strike.targetEntityId()).isEqualTo(CHARACTER_ID);
        assertThat(strike.damage()).isEqualTo(maxHp * 20 / 100);
        assertThat(strike.targetCurrentHp()).isEqualTo(maxHp - maxHp * 20 / 100);
        assertThat(strike.targetDied()).isFalse();
        assertThat(targets).containsExactly(CHARACTER_ID);
    }
}

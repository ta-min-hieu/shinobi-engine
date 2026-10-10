package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.kage.character.application.GrantExperienceUseCase;
import dev.kage.character.domain.ExperienceProgress;
import dev.kage.server.world.CharacterBaseStats;
import dev.kage.server.world.Element;
import dev.kage.server.world.TickMetricsPort;
import dev.kage.server.world.WorldFacade;
import dev.kage.server.world.WorldOutboundSink;

/**
 * {@link CharacterLevelSync} báo cú lên cấp cho ĐÚNG World — và không báo cho World đấu trường.
 *
 * <p>Đo bằng luật thật: mỗi World ở đây là một {@link WorldFacade} đang tick với một nhân vật Lv49
 * đứng cạnh quái mẫu; "World đã nhận cấp mới" nghĩa là Tuyệt Kỹ (cổng Lv50) tung được ở World đó.
 * Đường nối production ({@code GatewayBootstrap}) được chứng minh riêng ở
 * {@code LevelUpMidSessionDatabaseTest}.
 */
@Timeout(60)
class CharacterLevelSyncRoutingTest {

    private static final long CHARACTER_ID = 1L;
    private static final String ULTIMATE = "hoa_diem_nguc";
    private static final int GATE_LEVEL = 50;
    private static final CharacterBaseStats ONE_BELOW_GATE =
            new CharacterBaseStats(10, 10, 10, 10, Element.HOA, GATE_LEVEL - 1);

    /** Cạnh quái mẫu (5,5) của bãi tập — trong tầm Tuyệt Kỹ. */
    private static final float SPAWN_X = 5f;

    private static final GrantExperienceUseCase.Result LEVELED_UP_TO_GATE =
            new GrantExperienceUseCase.Result(new ExperienceProgress(GATE_LEVEL, 0L, 0L, 0L), true);
    private static final GrantExperienceUseCase.Result GATE_LEVEL_WITHOUT_LEVEL_UP =
            new GrantExperienceUseCase.Result(new ExperienceProgress(GATE_LEVEL, 0L, 0L, 0L), false);

    private final List<LiveWorld> worlds = new ArrayList<>();

    @AfterEach
    void stopWorlds() {
        worlds.forEach(world -> world.facade.stop());
    }

    @Test
    void shouldRaiseLevelInTheMainWorld_whenCharacterIsThere() {
        LiveWorld main = startWorldWithCharacter();
        CharacterLevelSync sync = syncOver(Optional.empty(), Optional.empty(), Optional.of(main));

        sync.onExperienceGranted(CHARACTER_ID, LEVELED_UP_TO_GATE);

        assertThat(main.castsUltimate()).isTrue();
    }

    /** Lúc đang dời về world chính cả hai định tuyến cùng tồn tại — thực thể còn ở hầm ngục. */
    @Test
    void shouldRaiseLevelInTheDungeon_notTheMainWorld_whenCharacterIsInADungeon() {
        LiveWorld dungeon = startWorldWithCharacter();
        LiveWorld main = startWorldWithCharacter();
        CharacterLevelSync sync = syncOver(Optional.empty(), Optional.of(dungeon), Optional.of(main));

        sync.onExperienceGranted(CHARACTER_ID, LEVELED_UP_TO_GATE);

        assertThat(dungeon.castsUltimate()).isTrue();
        assertThat(main.castsUltimate()).isFalse();
    }

    /** Cấp trong trận đấu giữ nguyên như lúc vào trận — xem javadoc {@link CharacterLevelSync}. */
    @Test
    void shouldLeaveLevelAlone_whileCharacterIsInADuel() {
        LiveWorld duel = startWorldWithCharacter();
        LiveWorld main = startWorldWithCharacter();
        CharacterLevelSync sync = syncOver(Optional.of(duel), Optional.empty(), Optional.of(main));

        sync.onExperienceGranted(CHARACTER_ID, LEVELED_UP_TO_GATE);

        assertThat(duel.castsUltimate()).isFalse();
        assertThat(main.castsUltimate()).isFalse();
    }

    /** Vạch EXP đổi mà không vượt mốc cấp: không có gì để báo, kể cả khi con số cấp trong gói đã cao. */
    @Test
    void shouldLeaveLevelAlone_whenTheGrantWasNotALevelUp() {
        LiveWorld main = startWorldWithCharacter();
        CharacterLevelSync sync = syncOver(Optional.empty(), Optional.empty(), Optional.of(main));

        sync.onExperienceGranted(CHARACTER_ID, GATE_LEVEL_WITHOUT_LEVEL_UP);

        assertThat(main.castsUltimate()).isFalse();
    }

    @Test
    void shouldDoNothing_whenCharacterIsInNoWorld() {
        CharacterLevelSync disconnected = syncOver(Optional.empty(), Optional.empty(), Optional.empty());

        assertThatCode(() -> disconnected.onExperienceGranted(CHARACTER_ID, LEVELED_UP_TO_GATE))
                .doesNotThrowAnyException();
        assertThatCode(() -> CharacterLevelSync.unbound().onExperienceGranted(CHARACTER_ID, LEVELED_UP_TO_GATE))
                .doesNotThrowAnyException();
    }

    private static CharacterLevelSync syncOver(
            Optional<LiveWorld> duel, Optional<LiveWorld> dungeon, Optional<LiveWorld> main) {
        return new CharacterLevelSync(
                id -> duel.map(world -> world.facade),
                new WorldRouteLookup(
                        List.of(id -> dungeon.map(world -> world.facade), id -> main.map(world -> world.facade))));
    }

    private LiveWorld startWorldWithCharacter() {
        LiveWorld world = new LiveWorld();
        worlds.add(world);
        world.facade.start();
        world.facade.attachCharacter(CHARACTER_ID, SPAWN_X, 0f, ONE_BELOW_GATE);
        world.awaitMonster();
        return world;
    }

    /** Một World đang tick + những gì nó phát ra mà lớp test này hỏi tới. */
    private static final class LiveWorld implements WorldOutboundSink {
        final List<Long> monsterIds = new CopyOnWriteArrayList<>();
        final List<String> castSkillCodes = new CopyOnWriteArrayList<>();
        final WorldFacade facade =
                new WorldFacade(this, true, TickMetricsPort.NO_OP, TestWorldContents.trainingGroundWithElder());

        void awaitMonster() {
            Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
            while (monsterIds.isEmpty()) {
                assertThat(Instant.now()).as("quái mẫu phải vào AoI").isBefore(deadline);
                facade.inboxBarrier().join();
            }
        }

        /** Gửi Tuyệt Kỹ và chờ trọn lượt tick chứa nó — {@code true} nếu World đã tung. */
        boolean castsUltimate() {
            facade.useSkill(CHARACTER_ID, ULTIMATE, monsterIds.get(0));
            facade.inboxBarrier().join();
            facade.inboxBarrier().join();
            return castSkillCodes.contains(ULTIMATE);
        }

        @Override
        public void onMonsterSpawned(long targetCharacterId, WorldOutboundSink.MonsterSnapshot monster) {
            monsterIds.add(monster.monsterId());
        }

        @Override
        public void onSkillResult(long targetCharacterId, WorldOutboundSink.SkillOutcome outcome) {
            castSkillCodes.add(outcome.skillCode());
        }

        @Override
        public void onMoveAccepted(WorldOutboundSink.MoveAcceptedSnapshot accepted) {}

        @Override
        public void onCharacterSpawned(long targetCharacterId, long spawnedCharacterId, float x, float y) {}

        @Override
        public void onCharacterDespawned(long targetCharacterId, long despawnedCharacterId) {}

        @Override
        public void onCharacterMoved(long targetCharacterId, long movedCharacterId, float x, float y) {}

        @Override
        public void onMonsterDespawned(long targetCharacterId, long monsterId) {}

        @Override
        public void onMonsterMoved(long targetCharacterId, long monsterId, float x, float y) {}

        @Override
        public void onMonsterKilled(long killerCharacterId, String monsterCode, long expReward) {}

        @Override
        public void onNpcSpawned(long targetCharacterId, WorldOutboundSink.NpcSnapshot npc) {}

        @Override
        public void onNpcDespawned(long targetCharacterId, long npcId) {}

        @Override
        public void onAttackResult(long targetCharacterId, WorldOutboundSink.AttackOutcome outcome) {}

        @Override
        public void onAreaChatMessage(long targetCharacterId, long senderCharacterId, String senderName, String body) {}

        @Override
        public void onCharacterAppearanceChanged(long targetCharacterId, long changedCharacterId) {}

        @Override
        public void onHealthChanged(long targetCharacterId, WorldOutboundSink.HealthSnapshot health) {}

        @Override
        public void onCharacterDefeated(long defeatedCharacterId, long killerEntityId) {}

        @Override
        public void onCharacterRespawned(WorldOutboundSink.RespawnSnapshot respawn) {}
    }
}

package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import dev.kage.character.domain.Character;
import dev.kage.protocol.AchievementUnlockedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.DbMetricsPort;
import dev.kage.server.database.ServerDatabaseBootstrap;
import dev.kage.server.database.session.RedisConfig;

/**
 * Thành tựu (ADR-062) qua đúng composition root + {@link AchievementHooks} thật, trên PostgreSQL THẬT: mở
 * đúng lúc vượt mốc và báo đúng một lần, mở bù lúc vào world, đeo danh hiệu chỉ khi đã mở.
 */
@Testcontainers
@Timeout(240)
class AchievementDatabaseTest {

    private static final int POSTGRES_PORT = 5432;
    private static final int REDIS_PORT = 6379;
    private static final String REDIS_PASSWORD = "achv-redis-password";

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8"))
            .withExposedPorts(REDIS_PORT)
            .withCommand("redis-server", "--requirepass", REDIS_PASSWORD);

    private static final AtomicInteger NEXT = new AtomicInteger();
    private static GatewayFacades facades;
    private static final List<String> UNLOCKED = new CopyOnWriteArrayList<>();
    private static AchievementHooks hooks;

    @BeforeAll
    static void wire() {
        facades = GatewayBootstrap.wireFacades(new ServerDatabaseBootstrap(
                new DatabaseConfig(
                        POSTGRES.getHost(),
                        POSTGRES.getMappedPort(POSTGRES_PORT),
                        POSTGRES.getDatabaseName(),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword()),
                new RedisConfig(REDIS.getHost(), REDIS.getMappedPort(REDIS_PORT), REDIS_PASSWORD),
                DbMetricsPort.NO_OP));
        hooks = new AchievementHooks(facades.progression().achievementFacade(), (id, opcode, payload) -> {
            if (opcode == Opcode.OPCODE_ACHIEVEMENT_UNLOCKED_EVENT_VALUE) {
                try {
                    UNLOCKED.add(id + ":"
                            + AchievementUnlockedEvent.parseFrom(payload).getCode());
                } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                    throw new IllegalStateException(e);
                }
            }
        });
    }

    @Test
    void tenKillsShouldUnlockOnce_andTheTitleCanThenBeWorn() {
        Character hero = newCharacter();
        for (int i = 0; i < 12; i++) {
            hooks.onMonsterKilled(hero.id());
        }

        assertThat(UNLOCKED)
                .filteredOn(code -> code.startsWith(hero.id() + ":"))
                .containsExactly(hero.id() + ":slayer_10");
        assertThat(hooks.titleKeyOf(hero.id())).isEmpty();
        assertThat(hooks.wearTitle(hero.id(), "duel_1")).as("chưa mở").isFalse();
        assertThat(hooks.wearTitle(hero.id(), "slayer_10")).isTrue();
        assertThat(hooks.titleKeyOf(hero.id())).isEqualTo("title.slayer_10");
        assertThat(hooks.wearTitle(hero.id(), "")).isTrue();
        assertThat(hooks.titleKeyOf(hero.id())).isEmpty();
    }

    @Test
    void enteringTheWorldShouldUnlockLevelMilestonesAlreadyReached_andLoadTheWornTitle() {
        Character hero = newCharacter();
        hooks.onEnteredWorld(hero.id(), 12);
        hooks.wearTitle(hero.id(), "level_10");
        hooks.onLeftWorld(hero.id());

        hooks.onEnteredWorld(hero.id(), 12);

        assertThat(UNLOCKED)
                .filteredOn(code -> code.startsWith(hero.id() + ":"))
                .containsExactly(hero.id() + ":level_10");
        assertThat(hooks.titleKeyOf(hero.id()))
                .as("nạp lại từ DB khi vào world")
                .isEqualTo("title.level_10");
    }

    private static Character newCharacter() {
        int n = NEXT.incrementAndGet();
        long accountId = facades.accountFacade().login("achv" + n).accountId();
        return facades.characterFacade()
                .createCharacter(accountId, "Achv" + n, UUID.randomUUID().toString());
    }
}

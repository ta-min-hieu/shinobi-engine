package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * ADR-050 — vùng an toàn thôi chỉ hồi máu và bắt đầu CHẶN aggro. Bốn ô, bốn quyết định tách rời.
 *
 * <p><b>Vì sao là lớp RIÊNG.</b> Bốn ô này sinh ra trong {@link WorldFacadeTest} và đẩy lớp đó lên
 * {@code CyclomaticComplexity} 87 / trần 80 của PMD — đúng cái bẫy đã cắn {@code QuestIntegrationTest}
 * cùng ngày. Cách sửa đã thành luật của dự án: tách theo VÙNG NGHIỆP VỤ.
 *
 * <p>Lớp này mang {@link RecordingSink} RIÊNG, chỉ ghi ba tín hiệu nó thật sự hỏi
 * ({@code onMonsterSpawned}, {@code onAttackResult}, {@code onHealthChanged}); mười bốn phương thức
 * còn lại của {@link WorldOutboundSink} là no-op. Nhân bản 40 dòng bản mẫu ở đây rẻ hơn hẳn việc
 * kéo {@code RecordingSink} 17 record của lớp kia ra thành hạ tầng dùng chung — và nó giữ cho lớp
 * này đọc được một mình, không phải nhảy file.
 *
 * <p>Cả bốn ô dùng CHUNG một world ({@code TestWorldContents.withHunterAndZone}): một con CÓ gây sự
 * (aggro 6m, đứng yên ở x=8) ngay cạnh một vùng an toàn {@code [0, 7]}. Đo bằng con {@code aggro = 0}
 * thì mọi ô đều xanh kể cả khi luật hỏng — xem javadoc {@code TestMonsterCatalogs.roamingHunter}.
 */
@Timeout(60)
class SafeZoneShieldTest {

    private static final CharacterBaseStats DEFAULT_STATS = new CharacterBaseStats(10, 10, 10, 10, Element.HOA, 1);

    private record MonsterSpawned(long monsterId, String code) {}

    private record AttackResult(long attacker, long defender, boolean miss) {}

    private record HealthChanged(long entityId, int currentHp, int maxHp) {}

    /**
     * Sink tối thiểu — chỉ ba danh sách. ⚠️ KHÔNG ghi {@code onCharacterMoved}: đó là luồng sự kiện
     * cho NGƯỜI QUAN SÁT, mà world ở đây chỉ có một nhân vật nên không ai quan sát ai; chờ ở đó là
     * chờ tới hết giờ (bài học đã trả giá một lượt build).
     */
    private static final class RecordingSink implements WorldOutboundSink {
        final List<MonsterSpawned> monsterSpawned = new CopyOnWriteArrayList<>();
        final List<AttackResult> attackResults = new CopyOnWriteArrayList<>();
        final List<HealthChanged> healthChanges = new CopyOnWriteArrayList<>();

        @Override
        public void onMonsterSpawned(long targetCharacterId, WorldOutboundSink.MonsterSnapshot monster) {
            monsterSpawned.add(new MonsterSpawned(monster.monsterId(), monster.monsterCode()));
        }

        @Override
        public void onAttackResult(long targetCharacterId, WorldOutboundSink.AttackOutcome outcome) {
            attackResults.add(new AttackResult(outcome.attackerEntityId(), outcome.targetEntityId(), outcome.miss()));
        }

        @Override
        public void onHealthChanged(long targetCharacterId, WorldOutboundSink.HealthSnapshot health) {
            healthChanges.add(new HealthChanged(health.entityId(), health.currentHp(), health.maxHp()));
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
        public void onSkillResult(long targetCharacterId, WorldOutboundSink.SkillOutcome outcome) {}

        @Override
        public void onAreaChatMessage(long targetCharacterId, long senderCharacterId, String senderName, String body) {}

        @Override
        public void onCharacterAppearanceChanged(long targetCharacterId, long changedCharacterId) {}

        @Override
        public void onCharacterDefeated(long defeatedCharacterId, long killerEntityId) {}

        @Override
        public void onCharacterRespawned(WorldOutboundSink.RespawnSnapshot respawn) {}
    }

    /**
     * D1 — vòng quét aggro BỎ QUA người đang đứng trong vùng an toàn.
     *
     * <p>Người chơi đứng ở x=3, con quái ở x=8 với aggro 6m: khoảng cách 5m, tức NẰM TRONG tầm phát
     * hiện. Trước ADR-050 nó sẽ đuổi tới và đánh. Ô này khẳng định điều ngược lại — và nó phải chờ
     * ĐỦ LÂU để thất bại có thật: 3 giây là ~180 tick, thừa cho một con chạy 3 m/s vượt 3m.
     */
    @Test
    void shouldNeverTargetACharacterStandingInsideASafeZone() {
        RecordingSink sink = new RecordingSink();
        WorldFacade world =
                new WorldFacade(sink, true, TickMetricsPort.NO_OP, TestWorldContents.withHunterAndZone(false));
        try {
            world.start();
            world.attachCharacter(1L, 3f, 0f, DEFAULT_STATS);
            awaitTrue(() -> sink.monsterSpawned.stream()
                    .anyMatch(m -> m.code().equals(TestMonsterCatalogs.ROAMING_HUNTER_CODE)));
            long hunterId = sink.monsterSpawned.get(0).monsterId();

            assertStaysFalse(
                    () -> sink.attackResults.stream().anyMatch(a -> a.attacker() == hunterId),
                    Duration.ofSeconds(3),
                    "quái không được nhắm người đang đứng trong vùng an toàn (D1)");
        } finally {
            world.stop();
        }
    }

    /**
     * D2 — con đang ĐUỔI/đang ĐÁNH mà mục tiêu bước vào vùng thì bỏ cuộc.
     *
     * <p>Khác D1 ở chỗ con quái đã KHOÁ mục tiêu trước khi người chơi vào vùng, nên D1 một mình
     * không cứu được: vòng quét aggro không chạy lại cho một con đang ở trạng thái Chase/Attack.
     * Đây đúng là cách người chơi chết ở x=154 trong playtest 2026-09-17.
     */
    @Test
    void shouldBreakOffTheChase_whenTheTargetStepsIntoASafeZone() {
        RecordingSink sink = new RecordingSink();
        WorldFacade world =
                new WorldFacade(sink, true, TickMetricsPort.NO_OP, TestWorldContents.withHunterAndZone(false));
        try {
            world.start();
            world.attachCharacter(1L, 9f, 0f, DEFAULT_STATS); // NGOÀI vùng, sát con quái @8
            awaitTrue(() -> sink.monsterSpawned.stream()
                    .anyMatch(m -> m.code().equals(TestMonsterCatalogs.ROAMING_HUNTER_CODE)));
            long hunterId = sink.monsterSpawned.get(0).monsterId();

            awaitTrue(
                    () -> sink.attackResults.stream().anyMatch(a -> a.attacker() == hunterId),
                    Duration.ofSeconds(15)); // phải đánh được ÍT NHẤT một nhát, nếu không ô này vô nghĩa

            world.enqueueMoveCommand(1L, 1, -1f, 0f); // đi TÂY vào vùng [0, 7]
            // ⚠️ Cửa sổ phải TÍNH, không ước lượng. Bản đầu cho 2 giây: 5 m/s × 2s = 10m, từ x=9 là
            // vọt sang x=-1, tức RA KHỎI vùng ở phía TÂY — ô này đỏ đúng vì thế, còn ô D4 thì XANH
            // VÌ LÝ DO SAI. 0,6 giây ≈ 3m ⇒ dừng quanh x=6: trong vùng, mà vẫn trong tầm với của con
            // quái @8, nên "nó thôi đánh" mới là một phép đo có ý nghĩa.
            sleepQuietly(600);
            world.enqueueMoveCommand(1L, 2, 0f, 0f); // đứng lại

            long hitsOnArrival = sink.attackResults.stream()
                    .filter(a -> a.attacker() == hunterId)
                    .count();
            assertStaysFalse(
                    () -> sink.attackResults.stream()
                                    .filter(a -> a.attacker() == hunterId)
                                    .count()
                            > hitsOnArrival + 1, // +1: nhát đang bay lúc vừa qua biên không tính là lỗi
                    Duration.ofSeconds(4),
                    "quái phải bỏ cuộc khi mục tiêu vào vùng an toàn (D2)");
        } finally {
            world.stop();
        }
    }

    /**
     * D3 — đánh ra từ trong vùng thì MẤT quyền được che.
     *
     * <p>Không có luật này thì trạm tiếp ứng là chòi bắn tỉa: đứng trong vùng bắn ra, con quái bị D2
     * đuổi về, bắn tiếp, thắng mọi trận mà không mất giọt máu nào. Người chơi đứng ở x=6.5 — vẫn
     * TRONG vùng, nhưng đã trong tầm đánh 2m của con quái @8.
     *
     * <p>⚠️ Lá chắn phải đo bằng {@code secondsSinceAttackDealt}, KHÔNG phải
     * {@code secondsSinceCombat}: đồng hồ kia reset cả cho bên CHỊU đòn, nên dùng nó thì D3 vô hiệu
     * hoá chính D2 (ô ngay trên là chỗ điều đó lộ ra).
     */
    @Test
    void shouldLoseTheSafeZoneShield_whenAttackingFromInsideIt() {
        RecordingSink sink = new RecordingSink();
        WorldFacade world =
                new WorldFacade(sink, true, TickMetricsPort.NO_OP, TestWorldContents.withHunterAndZone(false));
        try {
            world.start();
            world.attachCharacter(1L, 6.5f, 0f, DEFAULT_STATS);
            awaitTrue(() -> sink.monsterSpawned.stream()
                    .anyMatch(m -> m.code().equals(TestMonsterCatalogs.ROAMING_HUNTER_CODE)));
            long hunterId = sink.monsterSpawned.get(0).monsterId();

            targetAndAttackUntil(
                    world,
                    hunterId,
                    () -> sink.attackResults.stream().anyMatch(a -> a.attacker() == hunterId),
                    Duration.ofSeconds(25));

            assertThat(sink.attackResults.stream().anyMatch(a -> a.attacker() == hunterId && a.defender() == 1L))
                    .as("ai vừa đánh ra thì vùng an toàn thôi che cho người đó (D3)")
                    .isTrue();
        } finally {
            world.stop();
        }
    }

    /**
     * D4 — vùng khai {@code heals = false} KHÔNG hồi đầy máu lúc bước vào.
     *
     * <p>Đối chứng của {@code WorldFacadeTest.shouldHealToFullImmediately_whenCharacterWalksIntoASafeZone}:
     * cùng kịch bản, khác đúng một cờ boolean. Cửa sổ chờ 3 giây cố ý NGẮN HƠN
     * {@code REGEN_DELAY_SECONDS} (8s) — nếu không, hồi máu ngoài chiến đấu sẽ tự phát gói và ô này
     * đỏ vì lý do không liên quan.
     */
    @Test
    void shouldNotHealOnEntry_whenTheZoneDoesNotDeclareHeals() {
        RecordingSink sink = new RecordingSink();
        WorldFacade world =
                new WorldFacade(sink, true, TickMetricsPort.NO_OP, TestWorldContents.withHunterAndZone(false));
        try {
            world.start();
            world.attachCharacter(1L, 9f, 0f, DEFAULT_STATS);
            awaitTrue(() -> sink.attackResults.stream().anyMatch(a -> a.defender() == 1L && !a.miss()));

            world.enqueueMoveCommand(1L, 1, -1f, 0f);
            sleepQuietly(600); // cùng phép tính với D2
            world.enqueueMoveCommand(1L, 2, 0f, 0f);

            assertStaysFalse(
                    () -> sink.healthChanges.stream().anyMatch(h -> h.entityId() == 1L && h.currentHp() == h.maxHp()),
                    Duration.ofSeconds(3),
                    "vùng khai heals=false không được hồi đầy máu lúc bước vào (D4)");
        } finally {
            world.stop();
        }
    }

    /**
     * Ngược với {@link #awaitTrue(Supplier)}: khẳng định điều kiện KHÔNG bao giờ đúng trong cả cửa
     * sổ. Một ô "không có gì xảy ra" mà chờ thiếu thời gian thì luôn xanh — kể cả khi luật hỏng.
     */
    private static void assertStaysFalse(Supplier<Boolean> condition, Duration window, String because) {
        Instant deadline = Instant.now().plus(window);
        while (Instant.now().isBefore(deadline)) {
            assertThat(condition.get()).as(because).isFalse();
            sleepQuietly(20);
        }
    }

    private static void targetAndAttackUntil(
            WorldFacade world, long targetEntityId, Supplier<Boolean> condition, Duration timeout) {
        world.setTarget(1L, targetEntityId);
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (Boolean.TRUE.equals(condition.get())) {
                return;
            }
            world.attack(1L);
            sleepQuietly(10);
        }
        throw new AssertionError("Điều kiện không đúng sau " + timeout + " vừa chờ vừa bấm đánh");
    }

    private static void awaitTrue(Supplier<Boolean> condition) {
        awaitTrue(condition, Duration.ofSeconds(5));
    }

    private static void awaitTrue(Supplier<Boolean> condition, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (Boolean.TRUE.equals(condition.get())) {
                return;
            }
            sleepQuietly(10);
        }
        throw new AssertionError("Điều kiện không đúng sau " + timeout + " chờ tick loop");
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("bị ngắt khi đang chờ tick loop", e);
        }
    }
}

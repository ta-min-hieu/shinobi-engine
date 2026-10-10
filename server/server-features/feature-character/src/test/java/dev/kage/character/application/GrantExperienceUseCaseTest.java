package dev.kage.character.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.kage.character.domain.Character;
import dev.kage.character.domain.CharacterPort;
import dev.kage.character.domain.CharacterStats;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.character.domain.PositionSnapshot;
import dev.kage.character.domain.Transaction;

/**
 * Cột {@code character.level} phải luôn khớp cấp suy ra từ {@code character.exp}.
 *
 * <p>Đường cong ({@code ExperienceCurve}): 1→2 tốn 100, 2→3 tốn 200, 3→4 tốn 300. Nên tổng 620 EXP
 * là cấp 4 (100+200+300, dư 20), còn 120 EXP là cấp 2 (dư 20).
 */
class GrantExperienceUseCaseTest {

    private static final long CHARACTER_ID = 7L;

    @Test
    void shouldSaveLevel_whenGrantCrossesThreshold() {
        FakeCharacterPort port = new FakeCharacterPort(1, 99L);
        GrantExperienceUseCase useCase = new GrantExperienceUseCase(port);

        GrantExperienceUseCase.Result result = useCase.execute(CHARACTER_ID, 1L);

        assertThat(result.leveledUp()).isTrue();
        assertThat(result.progress().level()).isEqualTo(2);
        assertThat(port.savedLevel).isEqualTo(2);
    }

    @Test
    void shouldNotWriteLevel_whenNothingChanged() {
        FakeCharacterPort port = new FakeCharacterPort(2, 120L);
        GrantExperienceUseCase useCase = new GrantExperienceUseCase(port);

        useCase.execute(CHARACTER_ID, 10L);

        // Tối ưu cũ vẫn giữ: không UPDATE cột không đổi giá trị vào bảng nóng nhất của game.
        assertThat(port.savedLevel).isNull();
    }

    /**
     * Lỗi thật, bắt được khi playtest 2026-09-05: EXP thưởng quest đi thẳng vào {@code character.exp}
     * (PostgresQuestExpAdapter) chứ không qua use case này, nên cột {@code level} tụt lại. Bản cũ so
     * {@code after.level() > before.level()} — cả hai đều suy từ {@code exp} — nên KHÔNG BAO GIỜ thấy
     * cột đang lệch. Ở đây exp=620 (đúng ra cấp 4) mà cột vẫn ghi 2.
     */
    @Test
    void shouldHealStoredLevel_whenExpWasGrantedOutsideThisUseCase() {
        FakeCharacterPort port = new FakeCharacterPort(2, 620L);
        GrantExperienceUseCase useCase = new GrantExperienceUseCase(port);

        GrantExperienceUseCase.Result result = useCase.execute(CHARACTER_ID, 0L);

        assertThat(result.progress().level()).isEqualTo(4);
        // leveledUp=false vì CHÍNH lần gọi này không cộng gì — client không có gì để ăn mừng.
        assertThat(result.leveledUp()).isFalse();
        assertThat(port.savedLevel).isEqualTo(4);
    }

    /**
     * Kiểm sống chuỗi quest arc 1 (2026-10-03): lĩnh thưởng quest là những cú lên cấp LỚN NHẤT của cả
     * arc (1→3, 3→5, 8→10 — mở chọn hệ phái, 13→15 — mở mặc bộ Ngoại Môn), nhưng mọi
     * {@code ExperienceChangedEvent} sau lĩnh thưởng đều mang {@code leveledUp=false} — đo trên dây
     * 6/6 lần — nên client không bao giờ hiện "lên cấp" cho chúng. EXP thưởng đã vào {@code exp} qua
     * đường tắt; cột {@code level} còn ghi cấp CŨ, tức đúng cấp mà client đang hiển thị.
     */
    @Test
    void shouldReportLevelUp_whenSyncingAfterQuestRewardCrossedThresholds() {
        FakeCharacterPort port = new FakeCharacterPort(2, 620L);
        GrantExperienceUseCase useCase = new GrantExperienceUseCase(port);

        GrantExperienceUseCase.Result result = useCase.syncAfterExternalGrant(CHARACTER_ID);

        assertThat(result.progress().level()).isEqualTo(4);
        assertThat(result.leveledUp()).isTrue();
        assertThat(port.savedLevel).isEqualTo(4);
    }

    @Test
    void shouldNotReportLevelUp_whenSyncingAfterQuestRewardStayedInSameLevel() {
        FakeCharacterPort port = new FakeCharacterPort(4, 650L);
        GrantExperienceUseCase useCase = new GrantExperienceUseCase(port);

        GrantExperienceUseCase.Result result = useCase.syncAfterExternalGrant(CHARACTER_ID);

        assertThat(result.progress().level()).isEqualTo(4);
        assertThat(result.leveledUp()).isFalse();
    }

    @Test
    void shouldNotSkipLevels_whenStoredLevelIsBehindAndAGrantCrossesAThreshold() {
        // exp=620 (cấp 4) nhưng cột kẹt ở 2; cộng thêm 380 lên 1000 = cấp 5 (100+200+300+400).
        FakeCharacterPort port = new FakeCharacterPort(2, 620L);
        GrantExperienceUseCase useCase = new GrantExperienceUseCase(port);

        GrantExperienceUseCase.Result result = useCase.execute(CHARACTER_ID, 380L);

        assertThat(result.progress().level()).isEqualTo(5);
        assertThat(port.savedLevel).isEqualTo(5);
    }

    /**
     * Giết 2 quái cùng một tick = 2 lượt cộng EXP trên 2 Virtual Thread. Lượt A cộng 1 (99→100, cấp
     * 2) nhưng TRƯỚC khi A kịp ghi cấp, lượt B cộng 200 (100→300, cấp 3) và ghi xong. Bản cũ để A
     * ghi đè 2 lên 3 — cột {@code level} LÙI, lệch với {@code exp} cho tới lần nhận EXP kế tiếp.
     */
    @Test
    void shouldNotMoveLevelBackwards_whenAnOlderConcurrentGrantWritesLast() {
        FakeCharacterPort port = new FakeCharacterPort(1, 99L);
        GrantExperienceUseCase useCase = new GrantExperienceUseCase(port);
        port.beforeFirstSave = () -> useCase.execute(CHARACTER_ID, 200L);

        useCase.execute(CHARACTER_ID, 1L);

        assertThat(port.exp).isEqualTo(300L);
        assertThat(port.savedLevel).isEqualTo(3);
    }

    /**
     * Hai quái chết sát nhau: mỗi lượt cộng EXP chạy trên một Virtual Thread và CẢ HAI đọc nhân vật
     * trước khi lượt nào kịp cộng. Lượt B đưa 90 → 110 (vượt mốc lên cấp 2); lượt A sau đó đưa
     * 110 → 130, không vượt mốc nào. Lên cấp phải được báo ĐÚNG MỘT LẦN — bản cũ so với lần đọc đã cũ
     * (90) nên A báo lại chính lần lên cấp đó lần thứ hai.
     */
    @Test
    void shouldAnnounceLevelUpOnce_whenTwoConcurrentGrantsBothReadBeforeEitherAdds() {
        FakeCharacterPort port = new FakeCharacterPort(1, 90L);
        GrantExperienceUseCase useCase = new GrantExperienceUseCase(port);
        GrantExperienceUseCase.Result[] interleaved = new GrantExperienceUseCase.Result[1];
        port.afterFirstFind = () -> interleaved[0] = useCase.execute(CHARACTER_ID, 20L);

        GrantExperienceUseCase.Result outer = useCase.execute(CHARACTER_ID, 20L);

        assertThat(port.exp).isEqualTo(130L);
        assertThat(port.savedLevel).isEqualTo(2);
        assertThat(interleaved[0].leveledUp()).isTrue();
        assertThat(outer.leveledUp()).as("110 -> 130 không vượt mốc nào").isFalse();
    }

    /**
     * Thưởng quest (+500, 110 → 610, cấp 2 → 4) đã commit qua đường tắt của quest, và một lượt cộng
     * EXP hạ quái chen vào TRƯỚC lượt đồng bộ mà gateway gọi ngay sau đó. Lượt hạ quái hoà giải cột
     * {@code level}, nên lượt đồng bộ không còn thấy gì để báo. Phải có MỘT bên báo cho client: bản
     * cũ để cả hai trả {@code leveledUp=false}, cú lên cấp 2 → 4 không bao giờ hiện.
     */
    @Test
    void shouldAnnounceLevelUp_whenAKillGrantHealsTheLevelBeforeTheQuestSyncRuns() {
        FakeCharacterPort port = new FakeCharacterPort(2, 610L);
        GrantExperienceUseCase useCase = new GrantExperienceUseCase(port);

        GrantExperienceUseCase.Result kill = useCase.execute(CHARACTER_ID, 20L);
        GrantExperienceUseCase.Result questSync = useCase.syncAfterExternalGrant(CHARACTER_ID);

        assertThat(port.savedLevel).isEqualTo(4);
        assertThat(kill.leveledUp() ^ questSync.leveledUp())
                .as("cấp 2 -> 4 phải được báo đúng một lần (kill=%s, sync=%s)", kill.leveledUp(), questSync.leveledUp())
                .isTrue();
    }

    /** Chỉ hiện thực những phương thức use case này thật sự gọi. */
    private static final class FakeCharacterPort implements CharacterPort {

        private final int storedLevel;
        private long exp;
        private Integer savedLevel;
        // Chạy ĐÚNG MỘT LẦN ngay sau lần findById đầu tiên — chen một lượt cộng EXP khác vào giữa
        // "đọc" và "cộng" của lượt đang chạy.
        private Runnable afterFirstFind = () -> {};
        // Chạy ĐÚNG MỘT LẦN ngay trước lần saveLevel đầu tiên — chen một lượt cộng EXP khác vào
        // giữa "cộng" và "ghi cấp" của lượt đang chạy, tái hiện tất định hai Virtual Thread về đích
        // ngược thứ tự.
        private Runnable beforeFirstSave = () -> {};

        private FakeCharacterPort(int storedLevel, long exp) {
            this.storedLevel = storedLevel;
            this.exp = exp;
        }

        @Override
        public Optional<Character> findById(long characterId) {
            // Bản chụp (record bất biến) lấy TRƯỚC khi hook chạy — như một lần đọc dòng thật.
            Optional<Character> snapshot = Optional.of(
                    new Character(characterId, 1L, 1, "Kagemaru", Faction.HOA, currentLevel(), exp, 0, Instant.EPOCH));
            Runnable hook = afterFirstFind;
            afterFirstFind = () -> {};
            hook.run();
            return snapshot;
        }

        private int currentLevel() {
            return savedLevel == null ? storedLevel : savedLevel;
        }

        @Override
        public long addExperience(long characterId, long amount) {
            exp += amount;
            return exp;
        }

        @Override
        public boolean saveLevel(long characterId, int level, long derivedFromExp) {
            Runnable hook = beforeFirstSave;
            beforeFirstSave = () -> {};
            hook.run();
            // Cùng hợp đồng với PostgresCharacterAdapter: exp chưa đổi VÀ cấp thật sự khác.
            if (exp != derivedFromExp || currentLevel() == level) {
                return false;
            }
            savedLevel = level;
            return true;
        }

        @Override
        public Character create(Transaction tx, NewCharacter newCharacter) {
            throw new UnsupportedOperationException("không dùng trong test này");
        }

        @Override
        public java.util.List<Character> listByAccount(long accountId) {
            throw new UnsupportedOperationException("không dùng trong test này");
        }

        @Override
        public Optional<Character> findByName(String name) {
            throw new UnsupportedOperationException("không dùng trong test này");
        }

        @Override
        public boolean existsByName(String name) {
            throw new UnsupportedOperationException("không dùng trong test này");
        }

        @Override
        public int countByAccount(long accountId) {
            throw new UnsupportedOperationException("không dùng trong test này");
        }

        @Override
        public int lockAccountAndCountCharacters(Transaction tx, long accountId) {
            throw new UnsupportedOperationException("không dùng trong test này");
        }

        @Override
        public PositionSnapshot loadPosition(long characterId) {
            throw new UnsupportedOperationException("không dùng trong test này");
        }

        @Override
        public void savePosition(long characterId, float x, float y) {
            throw new UnsupportedOperationException("không dùng trong test này");
        }

        @Override
        public CharacterStats loadStats(long characterId) {
            throw new UnsupportedOperationException("không dùng trong test này");
        }

        @Override
        public long loadGold(long characterId) {
            throw new UnsupportedOperationException("không dùng trong test này");
        }

        @Override
        public void updateFaction(long characterId, Faction faction) {
            throw new UnsupportedOperationException("không dùng trong test này");
        }
    }
}

package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** ADR-081 D3 — sổ sát thương chỉ cho boss thế giới, chỉ đòn của nhân vật, đóng đúng một lần. */
class WorldBossDamageLedgerTest {

    @Test
    void shouldSumDamagePerCharacter_onlyForTrackedBosses() {
        WorldBossDamageLedger ledger = new WorldBossDamageLedger();
        ledger.track(-10L, "sample_boss", 100);

        ledger.record(-10L, 1L, 30, 70);
        ledger.record(-10L, 2L, 5, 65);
        ledger.record(-10L, 1L, 12, 53);
        ledger.record(-10L, -3L, 9, 44); // quái khác đánh — bỏ qua
        ledger.record(-10L, 2L, 0, 44); // trượt — bỏ qua
        ledger.record(-10L, 2L, 76, 0); // đòn kết liễu: chỉ 44 máu thật còn lại
        ledger.record(-11L, 1L, 50, 0); // quái thường — không có sổ

        assertThat(ledger.close(-11L)).isEmpty();
        WorldBossDamageLedger.Defeat defeat = ledger.close(-10L).orElseThrow();
        assertThat(defeat.monsterCode()).isEqualTo("sample_boss");
        assertThat(defeat.damageByCharacter()).isEqualTo(Map.of(1L, 42L, 2L, 5L + 44L));
        assertThat(ledger.close(-10L)).as("đóng một lần").isEmpty();
    }

    @Test
    void standingsShouldBeACopyOfLiveBossesOnly() {
        WorldBossDamageLedger ledger = new WorldBossDamageLedger();
        ledger.track(-10L, "demon_king", 1000);
        ledger.record(-10L, 1L, 30, 970);

        java.util.List<WorldBossStanding> standings = ledger.standings();
        ledger.record(-10L, 1L, 10, 960);

        assertThat(standings).singleElement().satisfies(standing -> {
            assertThat(standing.bossCode()).isEqualTo("demon_king");
            assertThat(standing.damageByCharacter())
                    .as("ảnh chụp, không phải sổ sống")
                    .isEqualTo(Map.of(1L, 30L));
        });
        ledger.close(-10L);
        assertThat(ledger.standings()).isEmpty();
    }
}
